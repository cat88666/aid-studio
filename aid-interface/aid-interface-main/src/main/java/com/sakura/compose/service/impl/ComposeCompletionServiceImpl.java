package com.sakura.compose.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraEpisodeEditorMapper;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.compose.ComposeConstants;
import com.sakura.compose.domain.ComposeBillingSnapshot;
import com.sakura.compose.service.ComposeBillingService;
import com.sakura.compose.service.ComposeCompletionService;
import com.sakura.compose.service.ComposeBatchSlotCoordinator;
import com.sakura.media.enums.MediaBillingStatus;
import com.sakura.media.provider.ProviderTaskResult;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 合成终态收口结算/退款实现。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComposeCompletionServiceImpl implements ComposeCompletionService {

    /** 合成中进度上限：100 保留给成功收口 */
    private static final int MAX_RUNNING_PROGRESS = 99;

    /** 媒体任务 Mapper */
    private final SakuraMediaTaskMapper sakuraMediaTaskMapper;

    /** 剧集剪辑 Mapper（接口2 回写） */
    private final SakuraEpisodeEditorMapper sakuraEpisodeEditorMapper;

    /** 合成计费服务 */
    private final ComposeBillingService composeBillingService;

    private final ComposeBatchSlotCoordinator composeBatchSlotCoordinator;

    @Override
    public void onSucceeded(SakuraMediaTask task, ProviderTaskResult result) {
        ComposeBillingSnapshot snapshot = parseSnapshot(task);
        // 实际输出秒数：优先取 MPS 解析值，缺失回退预估秒数（避免漏结算）
        long actualSeconds = resolveActualSeconds(result, snapshot);
        if (Objects.nonNull(snapshot)) {
            composeBillingService.settle(task.getUserId(), actualSeconds, snapshot, task.getBillingTraceId());
        } else {
            log.warn("合成成功但计费快照缺失, 跳过结算, taskId={}", task.getId());
        }
        // 持久化实际输出时长 + 计费状态（成片业务回写由 OSS 持久化事件以相对路径完成）
        LambdaUpdateWrapper<SakuraMediaTask> update = new LambdaUpdateWrapper<>();
        update.eq(SakuraMediaTask::getId, task.getId());
        update.set(SakuraMediaTask::getOutputDurationSeconds, actualSeconds);
        update.set(SakuraMediaTask::getBillingStatus, MediaBillingStatus.SUCCESS.name());
        update.set(SakuraMediaTask::getFrozenAmount, BigDecimal.ZERO);
        update.set(SakuraMediaTask::getUpdateTime, new Date());
        sakuraMediaTaskMapper.update(null, update);
        if (ComposeConstants.CALLBACK_EPISODE_EDITOR.equalsIgnoreCase(task.getCallbackCategory())
                && Objects.nonNull(task.getCallbackRecordId())) {
            composeBatchSlotCoordinator.releaseExport(
                    task.getCallbackRecordId(), String.valueOf(task.getId()));
        } else if (StrUtil.isNotBlank(task.getComposeBatchId())) {
            composeBatchSlotCoordinator.releaseVoiceover(task.getComposeBatchId());
        }
        log.info("合成成功收口完成, taskId={}, actualSeconds={}", task.getId(), actualSeconds);
    }

    @Override
    public void onFailed(SakuraMediaTask task, ProviderTaskResult result) {
        ComposeBillingSnapshot snapshot = parseSnapshot(task);
        if (Objects.nonNull(snapshot)) {
            composeBillingService.refund(task.getUserId(), snapshot, task.getBillingTraceId());
        } else {
            log.warn("合成失败但计费快照缺失, 跳过退款, taskId={}", task.getId());
        }
        LambdaUpdateWrapper<SakuraMediaTask> update = new LambdaUpdateWrapper<>();
        update.eq(SakuraMediaTask::getId, task.getId());
        update.set(SakuraMediaTask::getBillingStatus, MediaBillingStatus.FAILED.name());
        update.set(SakuraMediaTask::getFrozenAmount, BigDecimal.ZERO);
        update.set(SakuraMediaTask::getUpdateTime, new Date());
        sakuraMediaTaskMapper.update(null, update);
        // 接口2 失败终态回写：exportStatus=3 + errorMsg
        if (ComposeConstants.CALLBACK_EPISODE_EDITOR.equalsIgnoreCase(task.getCallbackCategory())
                && Objects.nonNull(task.getCallbackRecordId())) {
            writeEpisodeEditorFailed(task, result);
            composeBatchSlotCoordinator.releaseExport(
                    task.getCallbackRecordId(), String.valueOf(task.getId()));
        } else if (StrUtil.isNotBlank(task.getComposeBatchId())) {
            composeBatchSlotCoordinator.releaseVoiceover(task.getComposeBatchId());
        }
        log.info("合成失败收口完成, taskId={}", task.getId());
    }

    @Override
    public void onProgress(SakuraMediaTask task, Integer progress) {
        // 仅接口2 任务回写进度；进度为空/非法直接忽略
        if (Objects.isNull(progress)
                || !ComposeConstants.CALLBACK_EPISODE_EDITOR.equalsIgnoreCase(task.getCallbackCategory())
                || Objects.isNull(task.getCallbackRecordId())) {
            return;
        }
        // 收敛到 [0,99]：100 由成功收口写入，避免"进度100但状态还是合成中"的错觉
        int clamped = Math.max(0, Math.min(MAX_RUNNING_PROGRESS, progress));
        try {
            LambdaUpdateWrapper<SakuraEpisodeEditor> update = new LambdaUpdateWrapper<>();
            update.eq(SakuraEpisodeEditor::getId, task.getCallbackRecordId());
            update.eq(SakuraEpisodeEditor::getExportTaskId, String.valueOf(task.getId()));
            // 仅在仍「合成中」时回写，且进度只增不减（并发轮询/回调乱序时防回退）
            update.eq(SakuraEpisodeEditor::getExportStatus, ComposeConstants.EXPORT_STATUS_COMPOSING);
            update.and(w -> w.isNull(SakuraEpisodeEditor::getExportProgress)
                    .or().lt(SakuraEpisodeEditor::getExportProgress, clamped));
            update.set(SakuraEpisodeEditor::getExportProgress, clamped);
            update.set(SakuraEpisodeEditor::getUpdateTime, new Date());
            sakuraEpisodeEditorMapper.update(null, update);
        } catch (Exception ex) {
            // 进度回写属于展示增强，失败不影响调度主链路
            log.warn("合成进度回写失败, taskId={}, episodeEditorId={}, progress={}",
                    task.getId(), task.getCallbackRecordId(), progress, ex);
        }
    }

    /**
     * 回写 sakura_episode_editor 失败终态。
     *
     * @param task   COMPOSE 任务
     * @param result Provider 查询结果
     */
    private void writeEpisodeEditorFailed(SakuraMediaTask task, ProviderTaskResult result) {
        String errorMsg = Objects.nonNull(result) && StrUtil.isNotBlank(result.getErrorMessage())
                ? result.getErrorMessage() : "合成失败";
        LambdaUpdateWrapper<SakuraEpisodeEditor> update = new LambdaUpdateWrapper<>();
        update.eq(SakuraEpisodeEditor::getId, task.getCallbackRecordId());
        update.eq(SakuraEpisodeEditor::getExportTaskId, String.valueOf(task.getId()));
        update.eq(SakuraEpisodeEditor::getExportStatus, ComposeConstants.EXPORT_STATUS_COMPOSING);
        update.set(SakuraEpisodeEditor::getExportStatus, ComposeConstants.EXPORT_STATUS_FAILED);
        update.set(SakuraEpisodeEditor::getErrorMsg, errorMsg);
        update.set(SakuraEpisodeEditor::getUpdateTime, new Date());
        sakuraEpisodeEditorMapper.update(null, update);
    }

    /**
     * 解析任务上的合成计费快照。
     *
     * @param task COMPOSE 任务
     * @return 计费快照，缺失/解析失败返回 null
     */
    private ComposeBillingSnapshot parseSnapshot(SakuraMediaTask task) {
        if (StrUtil.isBlank(task.getBillingSnapshotJson())) {
            return null;
        }
        try {
            return JSON.parseObject(task.getBillingSnapshotJson(), ComposeBillingSnapshot.class);
        } catch (Exception e) {
            log.error("合成计费快照解析失败, taskId={}", task.getId(), e);
            return null;
        }
    }

    /**
     * 解析实际输出秒数：优先 MPS 实际时长，缺失回退预估秒数。
     *
     * @param result   Provider 查询结果
     * @param snapshot 计费快照
     * @return 实际秒数
     */
    private long resolveActualSeconds(ProviderTaskResult result, ComposeBillingSnapshot snapshot) {
        if (Objects.nonNull(result) && Objects.nonNull(result.getOutputDurationSeconds())
                && result.getOutputDurationSeconds() > 0) {
            return result.getOutputDurationSeconds();
        }
        return Objects.nonNull(snapshot) ? snapshot.getEstimatedSeconds() : 0L;
    }
}
