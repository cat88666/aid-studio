package com.sakura.media.service.impl;

import cn.hutool.core.util.StrUtil;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.service.ISakuraMediaTaskService;
import com.sakura.domain.vo.AiModelConfigVo;
import com.sakura.media.constants.MinimaxH3Constants;
import com.sakura.media.enums.MediaTaskStatus;
import com.sakura.media.provider.MinimaxH3CallbackSupport;
import com.sakura.media.provider.ProviderResponseHelper;
import com.sakura.media.service.MinimaxH3CallbackService;
import com.sakura.media.service.TaskDispatchService;
import com.sakura.service.IAiModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** MiniMax H3 回调只唤醒统一轮询，匿名端点不直接放大为上游查询。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinimaxH3CallbackServiceImpl implements MinimaxH3CallbackService {

    private final ISakuraMediaTaskService sakuraMediaTaskService;
    private final IAiModelConfigService aiModelConfigService;
    private final TaskDispatchService taskDispatchService;

    @Override
    public String handleCallback(String rawBody) {
        try {
            JsonNode root = ProviderResponseHelper.readTree(rawBody);
            String challenge = text(root, "challenge");
            if (StrUtil.isNotBlank(challenge)) {
                return challenge;
            }
            String providerTaskId = text(root == null ? null : root.path("task"), "id");
            if (StrUtil.isBlank(providerTaskId)) {
                providerTaskId = text(root, "task_id");
            }
            if (StrUtil.isBlank(providerTaskId)) {
                log.warn("MiniMax H3 callback missing task id");
                return null;
            }
            SakuraMediaTask task = findActiveTask(providerTaskId);
            if (task == null) {
                log.info("MiniMax H3 callback did not match an active task");
                return null;
            }
            AiModelConfigVo config = resolveModel(task);
            if (!isExpectedConfig(config)
                || StrUtil.isBlank(MinimaxH3CallbackSupport.resolveCallbackUrlForSubmission(config))) {
                log.warn("MiniMax H3 callback model configuration unavailable, taskId={}", task.getId());
                return null;
            }
            // 官方无签名：回调不能作为可信状态，也不能让匿名请求直接触发上游 GET。
            // 这里只做数据库 CAS 唤醒，权威查询、终态收口和用量结算仍由统一调度器完成。
            taskDispatchService.scheduleImmediatePollIfWaitingCallback(task);
        } catch (Exception ex) {
            log.error("MiniMax H3 callback processing failed; polling remains authoritative", ex);
        }
        return null;
    }

    private SakuraMediaTask findActiveTask(String providerTaskId) {
        LambdaQueryWrapper<SakuraMediaTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SakuraMediaTask::getId, SakuraMediaTask::getModelName, SakuraMediaTask::getProtocol,
                SakuraMediaTask::getProviderTaskId, SakuraMediaTask::getStatus, SakuraMediaTask::getUserId)
            .eq(SakuraMediaTask::getProviderTaskId, providerTaskId)
            .eq(SakuraMediaTask::getProtocol, MinimaxH3Constants.PROTOCOL_VIDEO)
            // Anonymous callbacks may wake a task exactly once. The scheduler performs
            // the WAIT_CALLBACK -> WAIT_POLL CAS; later notifications are handled by polling.
            .eq(SakuraMediaTask::getStatus, MediaTaskStatus.WAIT_CALLBACK.name())
            .orderByDesc(SakuraMediaTask::getId)
            .last("LIMIT 1");
        return sakuraMediaTaskService.getOne(wrapper, false);
    }

    private AiModelConfigVo resolveModel(SakuraMediaTask task) {
        try {
            return aiModelConfigService.selectByModelCodeForUser(task.getModelName(), task.getUserId());
        } catch (Exception ex) {
            log.warn("MiniMax H3 callback cannot resolve model, taskId={}, error={}",
                task.getId(), ex.getClass().getSimpleName());
            return null;
        }
    }

    private boolean isExpectedConfig(AiModelConfigVo config) {
        return config != null
            && (MinimaxH3Constants.PROVIDER_CODE.equalsIgnoreCase(StrUtil.trim(config.getProviderCode()))
                || MinimaxH3Constants.LEGACY_PROVIDER_CODE.equalsIgnoreCase(StrUtil.trim(config.getProviderCode())))
            && MinimaxH3Constants.PROTOCOL_VIDEO.equalsIgnoreCase(StrUtil.trim(config.getProtocol()))
            && MinimaxH3Constants.MODEL_CODES.contains(config.getModelCode());
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return StrUtil.trimToNull(node.path(field).asText(null));
    }
}
