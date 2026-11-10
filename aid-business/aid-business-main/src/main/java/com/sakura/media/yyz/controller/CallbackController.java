package com.sakura.media.yyz.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.sakura.common.annotation.Anonymous;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.media.dto.CallbackRequest;
import com.sakura.media.enums.MediaTaskStatus;
import com.sakura.media.service.TaskDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;

/**
 * 上游回调接收端点：各供应商通过此入口通知任务完成。
 * 当前 DashScope 等供应商暂不支持原生回调，此端点预留，默认关闭；
 * 启用时必须配置回调令牌，伪造回调（篡改任务终态触发结算/退款、污染结果地址）在入口即被拒绝。
 */
@Slf4j
@RestController
@RequestMapping("/api/callback/media")
@RequiredArgsConstructor
public class CallbackController {

    private final SakuraMediaTaskMapper sakuraMediaTaskMapper;
    private final TaskDispatchService taskDispatchService;
    private final ISakuraAiModelService sakuraAiModelService;
    private final ISakuraAiProviderService sakuraAiProviderService;

    /** 通用回调开关：默认关闭（当前无供应商使用该预留端点，Vidu 走专用验签端点） */
    @Value("${media.callback.generic-enabled:false}")
    private boolean genericCallbackEnabled;

    /** 通用回调令牌：开启后必须配置，回调 URL 携带 ?token= 与之常量时间比对 */
    @Value("${media.callback.generic-token:}")
    private String genericCallbackToken;

    /**
     * 接收供应商回调通知。
     * URL 格式：POST /api/callback/media/{providerCode}?token=xxx
     * 显式 @Anonymous 不依赖 SecurityConfig 的 permitAll 白名单，防止配置变更后回调接口返 401。
     *
     * @param providerCode 供应商编码（如 dashscope、volcengine）
     * @param token        回调令牌（与配置 media.callback.generic-token 比对）
     * @param request      回调请求体（通用结构）
     */
    @Anonymous
    @PostMapping("/{providerCode}")
    public AjaxResult onCallback(@PathVariable String providerCode,
                                 @RequestParam(required = false) String token,
                                 @RequestBody CallbackRequest request) {
        log.info("收到回调通知, providerCode={}, providerTaskId={}, status={}",
            providerCode, request.getProviderTaskId(), request.getStatus());

        // 来源校验：端点默认关闭；开启后令牌缺配或不匹配一律拒绝（fail-close，防伪造终态）
        if (!genericCallbackEnabled) {
            log.warn("通用媒体回调端点未启用，已拒绝, providerCode={}", providerCode);
            return AjaxResult.success();
        }
        if (StrUtil.isBlank(genericCallbackToken) || !constantTimeEquals(genericCallbackToken, token)) {
            log.warn("通用媒体回调令牌校验失败, providerCode={}, providerTaskId={}",
                providerCode, request.getProviderTaskId());
            return AjaxResult.success();
        }

        if (StrUtil.isBlank(request.getProviderTaskId())) {
            log.warn("回调缺少 providerTaskId, providerCode={}", providerCode);
            return AjaxResult.success();
        }

        LambdaQueryWrapper<SakuraMediaTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SakuraMediaTask::getProviderTaskId, request.getProviderTaskId());
        // 仅查找非终态任务。
        wrapper.in(SakuraMediaTask::getStatus,
            MediaTaskStatus.WAIT_CALLBACK.name(),
            MediaTaskStatus.WAIT_POLL.name(),
            MediaTaskStatus.PROCESSING.name());
        wrapper.last("LIMIT 1");

        SakuraMediaTask task = sakuraMediaTaskMapper.selectOne(wrapper);
        if (task == null) {
            log.info("回调未找到待处理任务, providerTaskId={}", request.getProviderTaskId());
            return AjaxResult.success();
        }

        // 校验路径 providerCode 与任务实际归属供应商一致，反查失败（null）同样拒绝，避免校验形同虚设。
        String actualProviderCode = resolveProviderCodeByModelName(task.getModelName());
        if (!StrUtil.equals(providerCode, actualProviderCode)) {
            log.warn("回调供应商校验拒绝, pathProviderCode={}, actualProviderCode={}, taskId={}, providerTaskId={}",
                providerCode, actualProviderCode, task.getId(), request.getProviderTaskId());
            return AjaxResult.success();
        }

        // 通用回调结构不是任何具体厂商的官方状态契约，不能直接用它结算/退款。
        // 只把任务唤醒为立即轮询，由对应 Provider 按官方查询格式确认终态。
        taskDispatchService.scheduleImmediatePoll(task);

        return AjaxResult.success();
    }

    /**
     * 常量时间字符串比对：避免逐字符短路比较被计时侧信道猜测令牌。
     *
     * @param expected 配置的令牌
     * @param actual   回调携带的令牌
     * @return true=一致
     */
    private boolean constantTimeEquals(String expected, String actual) {
        if (StrUtil.isBlank(actual)) {
            return false;
        }
        return MessageDigest.isEqual(Utf8.encode(expected), Utf8.encode(actual));
    }

    /**
     * 通过任务的 modelName 反查所属供应商编码。
     */
    private String resolveProviderCodeByModelName(String modelName) {
        if (StrUtil.isBlank(modelName)) {
            return null;
        }
        try {
            // 从 modelName 找到模型配置，再找其所属供应商。
            SakuraAiModel model = sakuraAiModelService.getOne(
                new LambdaQueryWrapper<SakuraAiModel>()
                    .eq(SakuraAiModel::getModelCode, modelName)
                    .last("LIMIT 1"),
                false
            );
            if (model == null || model.getProviderId() == null) {
                return null;
            }
            SakuraAiProvider provider = sakuraAiProviderService.getById(model.getProviderId());
            return provider != null ? provider.getProviderCode() : null;
        } catch (Exception ex) {
            log.warn("resolveProviderCodeByModelName 异常, modelName={}", modelName, ex);
            return null;
        }
    }
}
