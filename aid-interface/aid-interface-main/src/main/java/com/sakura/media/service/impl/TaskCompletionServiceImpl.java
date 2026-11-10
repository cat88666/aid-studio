package com.sakura.media.service.impl;

import com.sakura.common.error.TaskErrorResult;
import com.sakura.common.error.ErrorNormalizer;
import com.sakura.common.error.TaskErrorSnapshot;
import com.sakura.common.exception.ServiceException;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakura.sakura.domain.media.SakuraMediaResult;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.sakura.mapper.SakuraMediaResultMapper;
import com.sakura.billing.service.BillingFacadeService;
import com.sakura.compose.ComposeConstants;
import com.sakura.compose.service.ComposeCompletionService;
import com.sakura.media.enums.MediaTaskStatus;
import com.sakura.media.event.MediaTaskCompletedEvent;
import com.sakura.media.event.MediaTaskOssPersistedEvent;
import com.sakura.media.provider.ProviderTaskResult;
import com.sakura.media.provider.TextFailureBillingPolicy;
import com.sakura.media.service.MediaConcurrencyLimiter;
import com.sakura.media.service.MediaTaskArchiveService;
import com.sakura.media.service.TaskCompletionService;
import com.sakura.media.util.MediaTaskPayloadSanitizer;
import com.sakura.media.enums.MediaType;
import com.sakura.media.eta.MediaEtaRecorder;
import com.sakura.modelhealth.service.ModelHealthRecorder;
import com.sakura.domain.vo.AiModelConfigVo;
import com.sakura.model.definition.ModelTaskConfigurationResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 统一终态处理：回调与轮询都走同一入口，CAS 抢终态处理权，幂等收口。
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class TaskCompletionServiceImpl implements TaskCompletionService {

    private final SakuraMediaTaskMapper sakuraMediaTaskMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SakuraMediaResultMapper sakuraMediaResultMapper;
    private final BillingFacadeService billingFacadeService;
    private final MediaConcurrencyLimiter concurrencyLimiter;
    private final ApplicationEventPublisher eventPublisher;
    /** 合成终态收口（COMPOSE 分支，绕开模型计费） */
    private final ComposeCompletionService composeCompletionService;
    /** 终态请求/响应异步归档与数据库载荷压缩 */
    private final MediaTaskArchiveService mediaTaskArchiveService;
    /** 模型健康采集：终态收口点按成功/失败累加时间桶计数（仅上游结果，内部吞异常） */
    private final ModelHealthRecorder modelHealthRecorder;
    /** 可灵原始失败样本记录；内部完全隔离异常，不影响终态事务。 */
    private final KlingTerminalFailureRecorder klingTerminalFailureRecorder;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ModelTaskConfigurationResolver taskConfigurationResolver;

    /** ETA 成功样本旁路采集；字段注入保持既有测试构造器源兼容。 */
    @org.springframework.beans.factory.annotation.Autowired
    private MediaEtaRecorder mediaEtaRecorder;

    /** 媒体类型：合成任务，走独立计费/回写分支 */
    private static final String COMPOSE_MEDIA_TYPE = ComposeConstants.MEDIA_TYPE_COMPOSE;

    /** 保留模块内既有直接构造调用的源兼容；文件存储已不再参与通用终态构造。 */
    TaskCompletionServiceImpl(SakuraMediaTaskMapper sakuraMediaTaskMapper,
                              BillingFacadeService billingFacadeService,
                              MediaConcurrencyLimiter concurrencyLimiter,
                              ApplicationEventPublisher eventPublisher,
                              ComposeCompletionService composeCompletionService,
                              com.sakura.common.sakura.oss.config.OssConfigManager ignoredOssConfigManager,
                              MediaTaskArchiveService mediaTaskArchiveService,
                              ModelHealthRecorder modelHealthRecorder,
                              KlingTerminalFailureRecorder klingTerminalFailureRecorder) {
        this(sakuraMediaTaskMapper, billingFacadeService, concurrencyLimiter, eventPublisher,
                composeCompletionService, mediaTaskArchiveService, modelHealthRecorder,
                klingTerminalFailureRecorder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(Long taskId, ProviderTaskResult taskResult) {
        SakuraMediaTask task = sakuraMediaTaskMapper.selectById(taskId);
        if (Objects.isNull(task)) {
            log.warn("completeTask 任务不存在, taskId={}", taskId);
            return false;
        }

        String currentStatus = task.getStatus();
        boolean directLocalCompose = COMPOSE_MEDIA_TYPE.equals(task.getMediaType())
            && com.sakura.compose.config.MpsConfigManager.MODE_LOCAL_FFMPEG.equals(task.getProtocol())
            && MediaTaskStatus.PENDING.name().equals(currentStatus)
            && Boolean.TRUE.equals(taskResult.getQuerySuccessful())
            && Boolean.TRUE.equals(taskResult.getTerminalConfirmed());
        boolean canTransition = MediaTaskStatus.WAIT_POLL.name().equals(currentStatus)
            || MediaTaskStatus.WAIT_CALLBACK.name().equals(currentStatus)
            || MediaTaskStatus.PROCESSING.name().equals(currentStatus)
            || directLocalCompose;
        if (!canTransition) {
            log.info("completeTask 任务已终态, taskId={}, status={}", taskId, currentStatus);
            return false;
        }

        // 已有上游任务ID时，查询异常或未经厂商文档终态确认的结果不得进入结算/退款。
        if (StrUtil.isNotBlank(task.getProviderTaskId())
                && (!Boolean.TRUE.equals(taskResult.getQuerySuccessful())
                || !Boolean.TRUE.equals(taskResult.getTerminalConfirmed()))) {
            log.warn("completeTask 拒绝未经上游终态确认的收口, taskId={}, providerTaskId={}, providerStatus={}",
                    taskId, task.getProviderTaskId(), taskResult.getProviderStatus());
            return false;
        }

        String targetStatus;
        if (MediaTaskStatus.SUCCEEDED.name().equals(taskResult.getStatus())) {
            targetStatus = MediaTaskStatus.SUCCEEDED.name();
        } else if (MediaTaskStatus.FAILED.name().equals(taskResult.getStatus())) {
            targetStatus = MediaTaskStatus.FAILED.name();
        } else {
            return false;
        }

        TencentStoredAudioResults tencentStoredAudio = null;
        if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)
                && TENCENT_CI_PROTOCOL.equalsIgnoreCase(task.getProtocol())) {
            try {
                tencentStoredAudio = resolveTencentStoredAudioResults(task, taskResult);
            } catch (RuntimeException ex) {
                log.error("腾讯云数据万象成功结果未完成权威转存，保留任务继续对账, taskId={}, error={}",
                        taskId, ex.getMessage());
                return false;
            }
        }
        TencentStoredVideoResult tencentStoredVideo = null;
        if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)
                && tencentStoredAudio == null
                && ((MediaType.VIDEO.name().equals(task.getMediaType())
                    && (TENCENT_CI_PROTOCOL.equalsIgnoreCase(task.getProtocol())
                        || "tencent-mps:subtitle-erase".equalsIgnoreCase(task.getProtocol())))
                    || COMPOSE_MEDIA_TYPE.equals(task.getMediaType())
                        && "tencent-mps".equalsIgnoreCase(task.getProtocol()))) {
            try {
                tencentStoredVideo = resolveTencentStoredVideoResult(taskResult);
            } catch (RuntimeException ex) {
                log.warn("腾讯云视频结果尚未持久化，继续等待任务对账, taskId={}, errorType={}",
                        taskId, ex.getClass().getSimpleName());
                return false;
            }
        }

        String userStr = task.getUserId() != null ? String.valueOf(task.getUserId()) : "";
        LambdaUpdateWrapper<SakuraMediaTask> casWrapper = new LambdaUpdateWrapper<>();
        casWrapper.eq(SakuraMediaTask::getId, taskId);
        if (directLocalCompose) {
            casWrapper.eq(SakuraMediaTask::getStatus, MediaTaskStatus.PENDING.name());
        } else {
            casWrapper.in(SakuraMediaTask::getStatus,
                MediaTaskStatus.WAIT_POLL.name(),
                MediaTaskStatus.WAIT_CALLBACK.name(),
                MediaTaskStatus.PROCESSING.name());
        }
        Date terminalTime = new Date();
        casWrapper.set(SakuraMediaTask::getStatus, targetStatus);
        casWrapper.set(SakuraMediaTask::getTerminalTime, terminalTime);
        casWrapper.set(SakuraMediaTask::getUpdateBy, userStr);
        casWrapper.set(SakuraMediaTask::getUpdateTime, new Date());
        MediaTaskArchiveService.PreparedTerminalPayload preparedPayload =
            mediaTaskArchiveService.prepareTerminalPayload(task, targetStatus, taskResult.getRawResponse());
        if (!Objects.equals(preparedPayload.getRequestJson(), task.getRequestJson())) {
            casWrapper.set(SakuraMediaTask::getRequestJson, preparedPayload.getRequestJson());
        }

        if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)) {
            casWrapper.set(SakuraMediaTask::getOriginUrl, taskResult.getResultUrl());
            if (tencentStoredAudio != null) {
                casWrapper.set(SakuraMediaTask::getOssUrl, tencentStoredAudio.storedUrls().get(0));
            }
            if (tencentStoredVideo != null) {
                casWrapper.set(SakuraMediaTask::getOssUrl, tencentStoredVideo.storedUrl());
            }
            casWrapper.set(SakuraMediaTask::getErrorMessage, null);
            casWrapper.set(SakuraMediaTask::getErrorDetailJson, null);
            // COMPOSE 成片已由所选云引擎直接写入当前对象存储，或由本地 FFmpeg 上传到当前存储；
            // 随 origin_url 一并保存对象相对路径，避免后续再次下载转存。读取层统一按资源访问地址拼接。
            if (COMPOSE_MEDIA_TYPE.equals(task.getMediaType()) && tencentStoredVideo == null) {
                String composeOssUrl = resolveComposeOssRelativePath(taskResult.getResultUrl());
                if (StrUtil.isNotBlank(composeOssUrl)) {
                    casWrapper.set(SakuraMediaTask::getOssUrl, composeOssUrl);
                }
            }
        } else {
            TaskErrorResult error = normalizeProviderFailure(taskId, task.getModelName(), taskResult);
            casWrapper.set(SakuraMediaTask::getErrorDetailJson,
                    TextFailureBillingPolicy.mergeErrorSnapshot(task.getErrorDetailJson(), error));
            casWrapper.set(SakuraMediaTask::getErrorMessage,
                MediaTaskPayloadSanitizer.sanitizeForStorage(error.getUserMessage()));
        }
        casWrapper.set(SakuraMediaTask::getResponseJson, preparedPayload.getResponseJson());

        int rows = sakuraMediaTaskMapper.update(null, casWrapper);
        if (rows == 0) {
            log.info("completeTask CAS 失败, taskId={} 已被其他路径处理", taskId);
            return false;
        }
        if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)) {
            if (tencentStoredAudio == null) {
                persistResultManifest(task, normalizeResultUrls(taskResult), userStr);
                if (tencentStoredVideo != null) {
                    persistTencentStoredVideoManifest(task, tencentStoredVideo, userStr);
                }
            } else {
                persistTencentStoredAudioManifest(task, tencentStoredAudio, userStr);
            }
        }
        task.setTerminalTime(terminalTime);
        if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus) && mediaEtaRecorder != null) {
            mediaEtaRecorder.recordSuccess(task);
        }
        if (MediaTaskStatus.FAILED.name().equals(targetStatus)
            && StrUtil.isNotBlank(taskResult.getRawErrorMessage())) {
            klingTerminalFailureRecorder.record(taskId, task.getModelName(), taskResult.getRawErrorMessage());
        }
        mediaTaskArchiveService.archiveAfterCommit(preparedPayload);

        task = sakuraMediaTaskMapper.selectById(taskId);

        // 模型健康采集：本方法是回调/轮询双路径的 CAS 收口点，同一任务只进一次，天然去重；
        // 此处的 SUCCEEDED/FAILED 均来自上游返回结果（含超时无响应关单），符合"只记上游错误"口径。
        recordModelHealth(task, targetStatus, taskResult);

        // 回调与轮询双路径经本方法 CAS 收口，同一 taskId 仅进入一次，故结算/退款天然幂等。
        if (COMPOSE_MEDIA_TYPE.equals(task.getMediaType())) {
            if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)) {
                composeCompletionService.onSucceeded(task, taskResult);
                // COMPOSE 直填 oss_url 后 oss_pending 恒为 0，OSS 补偿任务不会再扫到它；
                // 且 MPS 回调路径收口后没有 ensureOssPersisted 兜底（只有轮询路径有），
                // 若不在此处发布 OSS 持久化事件，ComposeResultListener 永远不触发，
                // sakura_episode_editor.final_video_url / export_status 将卡在合成中。
                // 故 oss_url 就绪时统一在收口点发布事件（监听器幂等，轮询路径重复发布无副作用）。
                if (StrUtil.isNotBlank(task.getOssUrl())) {
                    registerAfterCommitOssPersisted(task);
                }
            } else {
                composeCompletionService.onFailed(task, taskResult);
            }
            registerAfterCommitRelease(task);
            // 成功时返回 true，使调度赢家继续执行 OSS 回写（成片相对路径回填业务表由 OSS 事件完成）。
            return true;
        }

        Map<String, Object> settleUsage = buildSettleUsage(task, taskResult);
        boolean billingWon;
        if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)) {
            billingWon = billingFacadeService.settleBilling(
                task, settleUsage);
            log.info("completeTask 任务成功, taskId={}, billingWon={}", taskId, billingWon);
        } else if (MediaType.TEXT.name().equals(task.getMediaType())
                && task.getBillingStatus() != null
                && TextFailureBillingPolicy.shouldSettle(false,
                    task.getUpstreamAcceptTime() != null, task.getProtocol(),
                    task.getErrorDetailJson(), settleUsage)) {
            // Provider 已受理/已开始调用后，即使失败且 usage 缺失也按预冻结上限保守结算。
            billingWon = billingFacadeService.settleBilling(
                task, settleUsage);
            log.info("completeTask 文本Provider调用后失败，保守结算, taskId={}, billingWon={}",
                taskId, billingWon);
        } else {
            billingWon = billingFacadeService.refundBilling(task);
            log.info("completeTask 任务失败, taskId={}, error={}, billingWon={}",
                taskId, taskResult.getErrorMessage(), billingWon);
        }

        if (billingWon) {
            LambdaUpdateWrapper<SakuraMediaTask> billingUpdate = new LambdaUpdateWrapper<>();
            billingUpdate.eq(SakuraMediaTask::getId, taskId);
            billingUpdate.set(SakuraMediaTask::getActualCost, task.getActualCost());
            billingUpdate.set(SakuraMediaTask::getBillingSnapshotJson, task.getBillingSnapshotJson());
            billingUpdate.set(SakuraMediaTask::getBillingStatus, task.getBillingStatus());
            billingUpdate.set(SakuraMediaTask::getFrozenAmount, task.getFrozenAmount());
            billingUpdate.set(SakuraMediaTask::getUpdateBy, userStr);
            billingUpdate.set(SakuraMediaTask::getUpdateTime, new Date());
            sakuraMediaTaskMapper.update(null, billingUpdate);
        } else {
            SakuraMediaTask dbTask = sakuraMediaTaskMapper.selectById(taskId);
            if (dbTask != null) {
                task.setActualCost(dbTask.getActualCost());
                task.setBillingSnapshotJson(dbTask.getBillingSnapshotJson());
                task.setBillingStatus(dbTask.getBillingStatus());
                task.setFrozenAmount(dbTask.getFrozenAmount());
            }
        }

        // 必须在 afterCommit 中执行，确保 DB 终态落库成功后才释放名额和拉起新任务。
        registerAfterCommitRelease(task);

        if (billingWon && MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)) {
            return true;
        }
        return billingWon;
    }

    /** 保存供应商返回的全部有序结果；主任务继续以第 0 项兼容旧读取链路。 */
    private void persistResultManifest(SakuraMediaTask task, List<String> resultUrls, String operator) {
        if (sakuraMediaResultMapper == null || resultUrls.isEmpty()) {
            return;
        }
        for (int index = 0; index < resultUrls.size(); index++) {
            sakuraMediaResultMapper.upsertTaskResult(
                    task.getId(), index, task.getMediaType(), resultUrls.get(index), operator);
        }
    }

    /** Keep structured provider errors through polling/callback terminal persistence. */
    static TaskErrorResult normalizeProviderFailure(Long taskId, String model, ProviderTaskResult result) {
        TaskErrorResult explicit = result.getTaskError();
        if (!com.sakura.common.error.TaskErrorPresentation.isGeneric(explicit)) return explicit;
        String raw = StrUtil.blankToDefault(result.getRawErrorMessage(), result.getErrorMessage());
        TaskErrorResult text = ErrorNormalizer.classify(null, model, -1, raw);
        TaskErrorResult response = ErrorNormalizer.classify(null, model, -1, result.getRawResponse());
        if (!com.sakura.common.error.TaskErrorPresentation.isGeneric(response)
                && com.sakura.common.error.TaskErrorPresentation.specificity(response)
                >= com.sakura.common.error.TaskErrorPresentation.specificity(text)) {
            raw = result.getRawResponse();
        }
        return ErrorNormalizer.normalize(String.valueOf(taskId), null, model, -1, raw);
    }

    private static List<String> normalizeResultUrls(ProviderTaskResult taskResult) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(taskResult.getResultUrl())) {
            ordered.add(taskResult.getResultUrl().trim());
        }
        if (taskResult.getResultUrls() != null) {
            for (String resultUrl : taskResult.getResultUrls()) {
                if (StrUtil.isNotBlank(resultUrl)) ordered.add(resultUrl.trim());
            }
        }
        return new ArrayList<>(ordered);
    }

    private static final String TENCENT_CI_PROTOCOL = "tencent-ci-async-media";

    /** 服务端模型能力声明 AUDIO 输出时，要求 Provider 已完整转存全部有序结果。 */
    private TencentStoredAudioResults resolveTencentStoredAudioResults(SakuraMediaTask task,
                                                                        ProviderTaskResult taskResult) {
        if (taskConfigurationResolver == null) {
            throw new ServiceException("模型能力解析器不可用");
        }
        AiModelConfigVo config = taskConfigurationResolver.resolve(task);
        if (config == null || !TENCENT_CI_PROTOCOL.equalsIgnoreCase(config.getProtocol())
                || !"tencent_ci_media".equalsIgnoreCase(config.getProviderCode())) {
            throw new ServiceException("任务模型路由不匹配");
        }
        JSONObject capability;
        try {
            capability = JSON.parseObject(config.getCapabilityJson());
        } catch (RuntimeException ex) {
            throw new ServiceException("模型输出能力配置无效");
        }
        JSONArray outputModalities = capability == null
                ? null : capability.getJSONArray("outputModalities");
        if (outputModalities == null || outputModalities.size() != 1) {
            throw new ServiceException("模型输出能力配置缺失");
        }
        String outputModality = outputModalities.getString(0);
        if ("VIDEO".equalsIgnoreCase(outputModality)) {
            return null;
        }
        if (!"AUDIO".equalsIgnoreCase(outputModality)) {
            throw new ServiceException("模型输出能力配置不受支持");
        }
        Integer expectedCount = capability.getInteger("maxOutputCount");
        List<String> originUrls = normalizeResultUrls(taskResult);
        List<String> storedUrls = taskResult.getPersistedResultUrls();
        List<String> mimeTypes = taskResult.getPersistedResultMimeTypes();
        List<Long> fileSizes = taskResult.getPersistedResultFileSizes();
        int actualCount = originUrls.size();
        if (expectedCount == null || expectedCount < 1 || expectedCount > 2
                || actualCount < 1 || actualCount > expectedCount
                || (taskResult.getResultCount() != null && taskResult.getResultCount() != actualCount)
                || storedUrls == null || storedUrls.size() != actualCount
                || mimeTypes == null || mimeTypes.size() != actualCount
                || fileSizes == null || fileSizes.size() != actualCount
                || new LinkedHashSet<>(storedUrls).size() != actualCount) {
            throw new ServiceException("音频结果数量或转存清单不完整");
        }
        for (int index = 0; index < actualCount; index++) {
            String storedUrl = StrUtil.trim(storedUrls.get(index));
            String mimeType = StrUtil.trim(mimeTypes.get(index));
            Long fileSize = fileSizes.get(index);
            if (StrUtil.isBlank(storedUrl) || !storedUrl.startsWith("/") || storedUrl.startsWith("//")
                    || storedUrl.contains("://") || storedUrl.contains("..") || storedUrl.contains("\\")
                    || !Set.of("audio/aac", "audio/mp4", "audio/mpeg", "audio/flac", "audio/amr").contains(mimeType)
                    || fileSize == null || fileSize <= 0L) {
                throw new ServiceException("音频结果转存信息无效");
            }
        }
        return new TencentStoredAudioResults(originUrls, List.copyOf(storedUrls),
                List.copyOf(mimeTypes), List.copyOf(fileSizes));
    }

    private void persistTencentStoredAudioManifest(SakuraMediaTask task,
                                                    TencentStoredAudioResults results,
                                                    String operator) {
        if (sakuraMediaResultMapper == null) {
            throw new ServiceException("媒体结果存储不可用");
        }
        for (int index = 0; index < results.originUrls().size(); index++) {
            sakuraMediaResultMapper.upsertTaskResult(task.getId(), index, MediaType.AUDIO.name(),
                    results.originUrls().get(index), operator);
            LambdaUpdateWrapper<SakuraMediaResult> update = new LambdaUpdateWrapper<>();
            update.eq(SakuraMediaResult::getTaskId, task.getId());
            update.eq(SakuraMediaResult::getResultIndex, index);
            update.set(SakuraMediaResult::getMediaType, MediaType.AUDIO.name());
            update.set(SakuraMediaResult::getOssUrl, results.storedUrls().get(index));
            update.set(SakuraMediaResult::getMimeType, results.mimeTypes().get(index));
            update.set(SakuraMediaResult::getFileSize, results.fileSizes().get(index));
            update.set(SakuraMediaResult::getUpdateBy, operator);
            update.set(SakuraMediaResult::getUpdateTime, new Date());
            if (sakuraMediaResultMapper.update(null, update) != 1) {
                throw new ServiceException("音频结果权威登记失败");
            }
        }
    }

    private record TencentStoredAudioResults(List<String> originUrls, List<String> storedUrls,
                                             List<String> mimeTypes, List<Long> fileSizes) { }

    private TencentStoredVideoResult resolveTencentStoredVideoResult(ProviderTaskResult result) {
        List<String> stored = result.getPersistedResultUrls();
        List<String> mime = result.getPersistedResultMimeTypes();
        List<Long> sizes = result.getPersistedResultFileSizes();
        if (stored == null || stored.size() != 1 || mime == null || mime.size() != 1
                || sizes == null || sizes.size() != 1) {
            throw new ServiceException("视频结果尚未完成永久存储");
        }
        String url = StrUtil.trim(stored.get(0));
        if (StrUtil.isBlank(url) || !url.startsWith("/") || url.startsWith("//")
                || url.contains("://") || url.contains("..") || url.contains("\\")
                || !Set.of("video/mp4", "video/webm", "video/quicktime").contains(mime.get(0))
                || sizes.get(0) == null || sizes.get(0) <= 0) {
            throw new ServiceException("视频结果永久存储信息无效");
        }
        return new TencentStoredVideoResult(url, mime.get(0), sizes.get(0));
    }

    private void persistTencentStoredVideoManifest(SakuraMediaTask task, TencentStoredVideoResult result,
                                                   String operator) {
        if (sakuraMediaResultMapper == null) throw new ServiceException("媒体结果存储不可用");
        LambdaUpdateWrapper<SakuraMediaResult> update = new LambdaUpdateWrapper<>();
        update.eq(SakuraMediaResult::getTaskId, task.getId());
        update.eq(SakuraMediaResult::getResultIndex, 0);
        update.set(SakuraMediaResult::getOssUrl, result.storedUrl());
        update.set(SakuraMediaResult::getMimeType, result.mimeType());
        update.set(SakuraMediaResult::getFileSize, result.fileSize());
        update.set(SakuraMediaResult::getUpdateBy, operator);
        update.set(SakuraMediaResult::getUpdateTime, new Date());
        if (sakuraMediaResultMapper.update(null, update) != 1) {
            throw new ServiceException("视频结果权威登记失败");
        }
    }

    private record TencentStoredVideoResult(String storedUrl, String mimeType, long fileSize) { }

    /**
     * 模型健康采集：COMPOSE 为合成服务（MPS）非 AI 模型，不计入；
     * 成功耗时 = 任务创建到终态收口（异步生成任务的端到端生成耗时）。
     */
    private void recordModelHealth(SakuraMediaTask task, String targetStatus, ProviderTaskResult taskResult) {
        if (COMPOSE_MEDIA_TYPE.equals(task.getMediaType())) {
            return;
        }
        if (MediaTaskStatus.SUCCEEDED.name().equals(targetStatus)) {
            Long latencyMs = Objects.nonNull(task.getCreateTime())
                    ? System.currentTimeMillis() - task.getCreateTime().getTime() : null;
            modelHealthRecorder.recordSuccess(task.getModelName(), task.getMediaType(), latencyMs);
        } else {
            modelHealthRecorder.recordFailure(task.getModelName(), task.getMediaType(),
                    taskResult.getErrorMessage());
        }
    }

    /**
     * 统一终态入口按媒体类型传递实际用量，保证按张 / 按秒的结算依据不丢失。
     */
    private Map<String, Object> buildSettleUsage(SakuraMediaTask task, ProviderTaskResult taskResult) {
        if (Objects.equals(task.getMediaType(), MediaType.IMAGE.name())) {
            int actualCount = 0;
            if (Objects.nonNull(taskResult.getResultCount()) && taskResult.getResultCount() > 0) {
                actualCount = taskResult.getResultCount();
            } else if (taskResult.getResultUrls() != null && !taskResult.getResultUrls().isEmpty()) {
                actualCount = taskResult.getResultUrls().size();
            } else if (StrUtil.isNotBlank(taskResult.getResultUrl())) {
                actualCount = 1;
            }
            Map<String, Object> usage = new HashMap<>();
            usage.put("actualImageCount", Math.max(actualCount, 1));
            usage.put("resultCount", Math.max(actualCount, 1));
            return usage;
        }
        if (Objects.equals(task.getMediaType(), MediaType.VIDEO.name())) {
            Map<String, Object> usage = new HashMap<>();
            if (taskResult.getProviderCredits() != null && taskResult.getProviderCredits().signum() > 0) {
                usage.put("actualProviderCredits", taskResult.getProviderCredits());
            }
            if (Objects.nonNull(taskResult.getVideoDurationSeconds())
                    && taskResult.getVideoDurationSeconds() > 0) {
                usage.put("actualDuration", taskResult.getVideoDurationSeconds());
            }
            if (Objects.nonNull(taskResult.getInputVideoSeconds())
                    && taskResult.getInputVideoSeconds() >= 0) {
                usage.put("actualInputVideoSeconds", taskResult.getInputVideoSeconds());
            }
            if (Objects.nonNull(taskResult.getInputImageCount())
                    && taskResult.getInputImageCount() >= 0) {
                usage.put("actualInputImageCount", taskResult.getInputImageCount());
            }
            if (Objects.nonNull(taskResult.getCompletionTokens())
                    && taskResult.getCompletionTokens() > 0) {
                usage.put("completion_tokens", taskResult.getCompletionTokens());
                usage.put("output_tokens", taskResult.getCompletionTokens());
            }
            if (Objects.nonNull(taskResult.getTotalTokens())
                    && taskResult.getTotalTokens() > 0) {
                usage.put("total_tokens", taskResult.getTotalTokens());
            }
            return usage.isEmpty() ? null : usage;
        }
        return null;
    }

    /**
     * 解析 COMPOSE 成片的存储对象相对路径（以 {@code /} 起始，不含协议 / 域名 / query）。
     *
     * @param resultUrl 上游返回的成片地址
     * @return 对象相对路径，形如 {@code /compose_result/compose_xxx.mp4}；不适用时返回 null
     */
    private String resolveComposeOssRelativePath(String resultUrl) {
        if (StrUtil.isBlank(resultUrl)) {
            return null;
        }
        if (resultUrl.startsWith("/")) {
            return resultUrl;
        }
        try {
            // 取 URI path，剥离协议 / 域名 / query。
            String path = java.net.URI.create(resultUrl).getPath();
            if (StrUtil.isBlank(path)) {
                return null;
            }
            return path.startsWith("/") ? path : "/" + path;
        } catch (Exception e) {
            log.error("resolveComposeOssRelativePath 解析成片对象路径失败, url={}, err={}", resultUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 注册事务提交后回调：发布 OSS 持久化完成事件，驱动 ComposeResultListener 回填业务表
     * （sakura_episode_editor.final_video_url / export_status 或 sakura_gen_record）。
     * 必须在 afterCommit 中发布：监听器会重新 selectById 读任务，事务未提交时读不到终态。
     *
     * @param task 已终态且 oss_url 就绪的 COMPOSE 任务
     */
    private void registerAfterCommitOssPersisted(SakuraMediaTask task) {
        Long tid = task.getId();
        Long userId = task.getUserId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new MediaTaskOssPersistedEvent(this, tid, userId));
                }
            });
        } else {
            // 无事务上下文时直接发布（降级）。
            eventPublisher.publishEvent(new MediaTaskOssPersistedEvent(this, tid, userId));
        }
    }

    /**
     * 注册事务提交后回调：释放并发坑位并发布完成事件触发 drainQueue。
     * 无事务上下文时直接执行（降级）。COMPOSE 与既有分支共用，保证收口语义一致。
     *
     * @param task 已终态任务
     */
    private void registerAfterCommitRelease(SakuraMediaTask task) {
        Long userId = task.getUserId();
        Long tid = task.getId();
        // 释放需带模型编码：四维限流按 全局/用户/模型/供应商 各自计数，缺一会导致模型/供应商维度泄漏。
        String modelName = task.getModelName();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    concurrencyLimiter.release(userId, modelName);
                    eventPublisher.publishEvent(new MediaTaskCompletedEvent(this, tid, userId));
                }
            });
        } else {
            // 无事务上下文时直接执行（降级）。
            concurrencyLimiter.release(userId, modelName);
            eventPublisher.publishEvent(new MediaTaskCompletedEvent(this, tid, userId));
        }
    }

    /**
     * 未提交僵尸任务收口后的事务提交回调：仅 PENDING（已占槽）才释放并发坑位，统一触发 drainQueue。
     *
     * @param task       已被关闭的任务
     * @param wasPending 关闭前是否为 PENDING（占槽）
     */
    private void registerAfterCommitReleaseForUnsubmitted(SakuraMediaTask task, boolean wasPending) {
        Long userId = task.getUserId();
        Long tid = task.getId();
        // 释放需带模型编码：四维限流按 全局/用户/模型/供应商 各自计数，缺一会导致模型/供应商维度泄漏。
        String modelName = task.getModelName();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (wasPending) {
                        concurrencyLimiter.release(userId, modelName);
                    }
                    eventPublisher.publishEvent(new MediaTaskCompletedEvent(this, tid, userId));
                }
            });
        } else {
            if (wasPending) {
                concurrencyLimiter.release(userId, modelName);
            }
            eventPublisher.publishEvent(new MediaTaskCompletedEvent(this, tid, userId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeUnsubmittedTask(Long taskId, String errorMessage) {
        SakuraMediaTask task = sakuraMediaTaskMapper.selectById(taskId);
        if (Objects.isNull(task)) {
            log.warn("closeUnsubmittedTask 任务不存在, taskId={}", taskId);
            return false;
        }
        String currentStatus = task.getStatus();
        boolean canClose = MediaTaskStatus.PENDING.name().equals(currentStatus);
        if (!canClose) {
            log.info("closeUnsubmittedTask 任务已推进, taskId={}, status={}", taskId, currentStatus);
            return false;
        }
        // 本入口只允许 PENDING，它已占用上游并发坑位，收口后必须释放。
        final boolean wasPending = true;
        //    竞态——若期间状态已变，CAS 失败、留待下一轮重判，避免 wasPending 与实际占槽情况不一致导致漏释放。
        String userStr = task.getUserId() != null ? String.valueOf(task.getUserId()) : "";
        LambdaUpdateWrapper<SakuraMediaTask> casWrapper = new LambdaUpdateWrapper<>();
        casWrapper.eq(SakuraMediaTask::getId, taskId);
        casWrapper.eq(SakuraMediaTask::getStatus, currentStatus);
        casWrapper.set(SakuraMediaTask::getStatus, MediaTaskStatus.FAILED.name());
        casWrapper.set(SakuraMediaTask::getTerminalTime, new Date());
        casWrapper.set(SakuraMediaTask::getErrorDetailJson, TextFailureBillingPolicy.mergeErrorSnapshot(
                task.getErrorDetailJson(), ErrorNormalizer.normalize(
                        String.valueOf(taskId), null, task.getModelName(), -1, errorMessage)));
        casWrapper.set(SakuraMediaTask::getErrorMessage,
            MediaTaskPayloadSanitizer.sanitizeForStorage(errorMessage));
        MediaTaskArchiveService.PreparedTerminalPayload preparedPayload =
            mediaTaskArchiveService.prepareTerminalPayload(
                task, MediaTaskStatus.FAILED.name(), task.getResponseJson());
        if (!Objects.equals(preparedPayload.getRequestJson(), task.getRequestJson())) {
            casWrapper.set(SakuraMediaTask::getRequestJson, preparedPayload.getRequestJson());
        }
        casWrapper.set(SakuraMediaTask::getResponseJson, preparedPayload.getResponseJson());
        casWrapper.set(SakuraMediaTask::getUpdateBy, userStr);
        casWrapper.set(SakuraMediaTask::getUpdateTime, new Date());
        int rows = sakuraMediaTaskMapper.update(null, casWrapper);
        if (rows == 0) {
            log.info("closeUnsubmittedTask CAS 失败, taskId={} 已被其他路径处理", taskId);
            return false;
        }
        mediaTaskArchiveService.archiveAfterCommit(preparedPayload);
        task = sakuraMediaTaskMapper.selectById(taskId);
        if (COMPOSE_MEDIA_TYPE.equals(task.getMediaType())) {
            ProviderTaskResult zombieResult = ProviderTaskResult.builder()
                .status(MediaTaskStatus.FAILED.name())
                .errorMessage(errorMessage)
                .build();
            composeCompletionService.onFailed(task, zombieResult);
            registerAfterCommitReleaseForUnsubmitted(task, wasPending);
            return true;
        }
        boolean settleProviderCall = MediaType.TEXT.name().equals(task.getMediaType())
            && task.getBillingStatus() != null
            && TextFailureBillingPolicy.shouldSettle(false,
                task.getUpstreamAcceptTime() != null, task.getProtocol(),
                task.getErrorDetailJson(), Map.of());
        boolean billingWon = settleProviderCall
            ? billingFacadeService.settleBilling(task, Map.of())
            : billingFacadeService.refundBilling(task);
        log.info("closeUnsubmittedTask 关闭僵尸任务, taskId={}, billingWon={}", taskId, billingWon);
        if (billingWon) {
            LambdaUpdateWrapper<SakuraMediaTask> billingUpdate = new LambdaUpdateWrapper<>();
            billingUpdate.eq(SakuraMediaTask::getId, taskId);
            billingUpdate.set(SakuraMediaTask::getBillingStatus, task.getBillingStatus());
            billingUpdate.set(SakuraMediaTask::getFrozenAmount, task.getFrozenAmount());
            billingUpdate.set(SakuraMediaTask::getUpdateBy, userStr);
            billingUpdate.set(SakuraMediaTask::getUpdateTime, new Date());
            sakuraMediaTaskMapper.update(null, billingUpdate);
        }
        Long userId = task.getUserId();
        Long tid = task.getId();
        // 释放需带模型编码：四维限流按 全局/用户/模型/供应商 各自计数，缺一会导致模型/供应商维度泄漏。
        String modelName = task.getModelName();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (wasPending) {
                        concurrencyLimiter.release(userId, modelName);
                    }
                    eventPublisher.publishEvent(new MediaTaskCompletedEvent(this, tid, userId));
                }
            });
        } else {
            if (wasPending) {
                concurrencyLimiter.release(userId, modelName);
            }
            eventPublisher.publishEvent(new MediaTaskCompletedEvent(this, tid, userId));
        }
        return billingWon;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelQueuedTask(Long taskId, Long userId, String errorMessage) {
        SakuraMediaTask task = sakuraMediaTaskMapper.selectById(taskId);
        if (Objects.isNull(task)) {
            log.info("取消排队媒体任务跳过，任务不存在: taskId={}", taskId);
            return false;
        }
        if (!Objects.equals(userId, task.getUserId())) {
            log.warn("取消排队媒体任务拒绝，用户不匹配: taskId={}, userId={}", taskId, userId);
            return false;
        }
        if (!MediaTaskStatus.QUEUED.name().equals(task.getStatus())) {
            log.info("取消排队媒体任务跳过，任务已推进: taskId={}, status={}", taskId, task.getStatus());
            return false;
        }

        String userStr = String.valueOf(userId);
        String safeMessage = MediaTaskPayloadSanitizer.sanitizeForStorage(errorMessage);
        LambdaUpdateWrapper<SakuraMediaTask> casWrapper = new LambdaUpdateWrapper<>();
        casWrapper.eq(SakuraMediaTask::getId, taskId);
        casWrapper.eq(SakuraMediaTask::getUserId, userId);
        casWrapper.eq(SakuraMediaTask::getStatus, MediaTaskStatus.QUEUED.name());
        casWrapper.set(SakuraMediaTask::getStatus, MediaTaskStatus.FAILED.name());
        casWrapper.set(SakuraMediaTask::getTerminalTime, new Date());
        casWrapper.set(SakuraMediaTask::getErrorMessage, safeMessage);
        casWrapper.set(SakuraMediaTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage(safeMessage));
        MediaTaskArchiveService.PreparedTerminalPayload preparedPayload =
            mediaTaskArchiveService.prepareTerminalPayload(
                task, MediaTaskStatus.FAILED.name(), task.getResponseJson());
        if (!Objects.equals(preparedPayload.getRequestJson(), task.getRequestJson())) {
            casWrapper.set(SakuraMediaTask::getRequestJson, preparedPayload.getRequestJson());
        }
        casWrapper.set(SakuraMediaTask::getResponseJson, preparedPayload.getResponseJson());
        casWrapper.set(SakuraMediaTask::getUpdateBy, userStr);
        casWrapper.set(SakuraMediaTask::getUpdateTime, new Date());
        if (sakuraMediaTaskMapper.update(null, casWrapper) == 0) {
            log.info("取消排队媒体任务CAS未命中: taskId={}", taskId);
            return false;
        }

        mediaTaskArchiveService.archiveAfterCommit(preparedPayload);
        task = sakuraMediaTaskMapper.selectById(taskId);
        boolean billingWon = billingFacadeService.refundBilling(task);
        if (billingWon) {
            LambdaUpdateWrapper<SakuraMediaTask> billingUpdate = new LambdaUpdateWrapper<>();
            billingUpdate.eq(SakuraMediaTask::getId, taskId);
            billingUpdate.set(SakuraMediaTask::getBillingStatus, task.getBillingStatus());
            billingUpdate.set(SakuraMediaTask::getFrozenAmount, task.getFrozenAmount());
            billingUpdate.set(SakuraMediaTask::getUpdateBy, userStr);
            billingUpdate.set(SakuraMediaTask::getUpdateTime, new Date());
            sakuraMediaTaskMapper.update(null, billingUpdate);
        }
        registerAfterCommitReleaseForUnsubmitted(task, false);
        log.info("未提交媒体任务取消收口: taskId={}, billingWon={}", taskId, billingWon);
        return billingWon;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cancelQueuedUserTask(Long taskId, Long userId) {
        if (taskId == null || taskId <= 0 || userId == null || userId <= 0) {
            throw new ServiceException("任务参数错误");
        }
        SakuraMediaTask task = sakuraMediaTaskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            throw new ServiceException("任务不存在或无权操作");
        }
        if (!MediaType.IMAGE.name().equals(task.getMediaType())
                && !MediaType.VIDEO.name().equals(task.getMediaType())) {
            throw new ServiceException("该媒体任务暂不支持取消");
        }
        if (!"GENERIC_GENERATION".equals(task.getBizTaskType())) {
            throw new ServiceException("该媒体任务由原业务管理，请使用原任务取消入口");
        }
        String state = task.getStatus();
        if (MediaTaskStatus.CANCELLED.name().equals(state)) return "ALREADY_CANCELLED";
        if (!MediaTaskStatus.QUEUED.name().equals(state)) {
            return MediaTaskStatus.SUCCEEDED.name().equals(state) || MediaTaskStatus.FAILED.name().equals(state)
                    ? "FINISHED" : "IN_PROGRESS";
        }
        if (StrUtil.isNotBlank(task.getProviderTaskId()) || task.getUpstreamAcceptTime() != null) {
            return "IN_PROGRESS";
        }

        String reason = "用户取消，尚未提交上游";
        MediaTaskArchiveService.PreparedTerminalPayload payload =
                mediaTaskArchiveService.prepareTerminalPayload(task, MediaTaskStatus.CANCELLED.name(), task.getResponseJson());
        LambdaUpdateWrapper<SakuraMediaTask> update = new LambdaUpdateWrapper<>();
        update.eq(SakuraMediaTask::getId, taskId)
                .eq(SakuraMediaTask::getUserId, userId)
                .eq(SakuraMediaTask::getStatus, MediaTaskStatus.QUEUED.name())
                .and(w -> w.isNull(SakuraMediaTask::getProviderTaskId)
                        .or().eq(SakuraMediaTask::getProviderTaskId, ""))
                .isNull(SakuraMediaTask::getUpstreamAcceptTime);
        update.set(SakuraMediaTask::getStatus, MediaTaskStatus.CANCELLED.name());
        update.set(SakuraMediaTask::getTerminalTime, new Date());
        update.set(SakuraMediaTask::getErrorMessage, reason);
        update.set(SakuraMediaTask::getErrorDetailJson, null);
        update.set(SakuraMediaTask::getRequestJson, payload.getRequestJson());
        update.set(SakuraMediaTask::getResponseJson, payload.getResponseJson());
        update.set(SakuraMediaTask::getUpdateBy, String.valueOf(userId));
        update.set(SakuraMediaTask::getUpdateTime, new Date());
        if (sakuraMediaTaskMapper.update(null, update) == 0) {
            SakuraMediaTask current = sakuraMediaTaskMapper.selectById(taskId);
            if (current != null && MediaTaskStatus.CANCELLED.name().equals(current.getStatus())) return "ALREADY_CANCELLED";
            return current != null && (MediaTaskStatus.SUCCEEDED.name().equals(current.getStatus())
                    || MediaTaskStatus.FAILED.name().equals(current.getStatus())) ? "FINISHED" : "IN_PROGRESS";
        }
        mediaTaskArchiveService.archiveAfterCommit(payload);
        task = sakuraMediaTaskMapper.selectById(taskId);
        boolean billingWon = billingFacadeService.refundBilling(task);
        if (billingWon) {
            sakuraMediaTaskMapper.update(null, new LambdaUpdateWrapper<SakuraMediaTask>()
                    .eq(SakuraMediaTask::getId, taskId)
                    .set(SakuraMediaTask::getBillingStatus, task.getBillingStatus())
                    .set(SakuraMediaTask::getFrozenAmount, task.getFrozenAmount())
                    .set(SakuraMediaTask::getUpdateTime, new Date()));
        }
        registerAfterCommitReleaseForUnsubmitted(task, false);
        return "CANCELLED";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean requeueUnsubmittedTask(Long taskId) {
        SakuraMediaTask task = sakuraMediaTaskMapper.selectById(taskId);
        if (task == null || !MediaTaskStatus.PENDING.name().equals(task.getStatus())
                || StrUtil.isNotBlank(task.getProviderTaskId())) {
            return false;
        }
        LambdaUpdateWrapper<SakuraMediaTask> update = new LambdaUpdateWrapper<>();
        update.eq(SakuraMediaTask::getId, taskId);
        update.eq(SakuraMediaTask::getStatus, MediaTaskStatus.PENDING.name());
        update.and(w -> w.isNull(SakuraMediaTask::getProviderTaskId)
                .or().eq(SakuraMediaTask::getProviderTaskId, ""));
        update.set(SakuraMediaTask::getStatus, MediaTaskStatus.QUEUED.name());
        update.set(SakuraMediaTask::getErrorMessage, null);
        update.set(SakuraMediaTask::getErrorDetailJson, null);
        Date now = new Date();
        update.set(SakuraMediaTask::getNextPollTime, new Date(now.getTime() + 60_000L));
        update.set(SakuraMediaTask::getRetryCount, Objects.requireNonNullElse(task.getRetryCount(), 0) + 1);
        update.set(SakuraMediaTask::getUpdateTime, now);
        if (sakuraMediaTaskMapper.update(null, update) == 0) {
            return false;
        }
        registerAfterCommitReleaseForUnsubmitted(task, true);
        log.info("合成未确认任务已重新排队, taskId={}", taskId);
        return true;
    }
}
