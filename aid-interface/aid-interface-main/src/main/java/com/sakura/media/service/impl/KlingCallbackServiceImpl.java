package com.sakura.media.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.sakura.sakura.service.ISakuraMediaTaskService;
import com.sakura.common.core.redis.RedisCache;
import com.sakura.domain.vo.AiModelConfigVo;
import com.sakura.media.constants.KlingConstants;
import com.sakura.media.dto.KlingCallbackContext;
import com.sakura.media.enums.MediaTaskStatus;
import com.sakura.media.provider.KlingCallbackSignatureUtil;
import com.sakura.media.provider.KlingCallbackSupport;
import com.sakura.media.provider.KlingStatusMapper;
import com.sakura.media.provider.ProviderResponseHelper;
import com.sakura.media.provider.ProviderTaskResult;
import com.sakura.media.service.KlingCallbackService;
import com.sakura.media.service.KlingCallbackResult;
import com.sakura.media.service.TaskCompletionService;
import com.sakura.media.service.TaskDispatchService;
import com.sakura.service.IAiModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 可灵回调验签、幂等与统一终态收口。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KlingCallbackServiceImpl implements KlingCallbackService {

    private static final String IDEMPOTENCY_PREFIX = "kling:callback:webhook:";
    private static final int IDEMPOTENCY_TTL_DAYS = 7;
    private static final int PROCESSING_TTL_MINUTES = 5;
    private static final int TASK_CANDIDATE_LIMIT = 10;
    private static final String COMPLETED = "COMPLETED";

    private final ISakuraMediaTaskService sakuraMediaTaskService;
    private final ISakuraAiProviderService sakuraAiProviderService;
    private final IAiModelConfigService aiModelConfigService;
    private final TaskCompletionService taskCompletionService;
    private final TaskDispatchService taskDispatchService;
    private final RedisCache redisCache;

    @Override
    public KlingCallbackResult handleKlingCallback(String rawBody, KlingCallbackContext context) {
        try {
            return handle(rawBody, context);
        } catch (Exception ex) {
            log.error("Kling callback processing failed; polling remains authoritative", ex);
            return KlingCallbackResult.RETRYABLE_INTERNAL;
        }
    }

    private KlingCallbackResult handle(String rawBody, KlingCallbackContext context) {
        String providerSecret = resolveProviderWebhookSecret();
        boolean providerAuthenticated = verify(rawBody, context, providerSecret);
        JsonNode root = ProviderResponseHelper.readTree(rawBody);
        String providerTaskId = firstText(root, "id", "task_id");
        if (StrUtil.isBlank(providerTaskId)) {
            log.warn("Kling callback missing task id");
            return KlingCallbackResult.INVALID_SIGNATURE_OR_PAYLOAD;
        }
        List<SakuraMediaTask> candidates = findTaskCandidates(providerTaskId);
        if (candidates.isEmpty()) {
            if (!providerAuthenticated) {
                log.warn("Kling callback signature rejected for an unmatched task");
                return KlingCallbackResult.INVALID_SIGNATURE_OR_PAYLOAD;
            }
            log.info("Kling callback did not match a known task");
            // 平台密钥已证明请求来源可信；未知任务统一幂等确认，不向调用方暴露任务是否存在。
            return KlingCallbackResult.ACCEPTED;
        }
        SakuraMediaTask task = authenticateTask(candidates, rawBody, context);
        if (task == null) {
            log.warn("Kling callback signature rejected for matched task candidates, providerTaskId={}", providerTaskId);
            return KlingCallbackResult.INVALID_SIGNATURE_OR_PAYLOAD;
        }
        if (isTerminalTask(task)) {
            log.info("Kling callback duplicate for terminal task, taskId={}", task.getId());
            return KlingCallbackResult.ACCEPTED;
        }
        String providerStatus = firstText(root, "status", "task_status");
        if (!KlingStatusMapper.isKnown(providerStatus)) {
            log.warn("Kling callback contains unknown status, taskId={}, status={}", task.getId(), providerStatus);
            return KlingCallbackResult.INVALID_SIGNATURE_OR_PAYLOAD;
        }
        String normalized = KlingStatusMapper.normalize(providerStatus);
        ProviderTaskResult result = null;
        if (KlingStatusMapper.isTerminal(providerStatus)) {
            VideoOutput output = findVideoOutput(root);
            if (KlingConstants.TASK_STATUS_SUCCEEDED.equals(normalized) && StrUtil.isBlank(output.url())) {
                log.warn("Kling succeeded callback has no video output; polling will recheck, taskId={}", task.getId());
                return KlingCallbackResult.RETRYABLE_INTERNAL;
            }
            String rawError = KlingConstants.TASK_STATUS_FAILED.equals(normalized)
                ? firstText(root, "message", "task_status_msg") : null;
            result = ProviderTaskResult.builder()
                .status(normalized)
                .resultUrl(output.url())
                .videoDurationSeconds(output.durationSeconds())
                .errorMessage(KlingConstants.TASK_STATUS_FAILED.equals(normalized)
                    ? safeFailure(rawError) : null)
                .rawErrorMessage(rawError)
                .rawResponse(rawBody)
                .querySuccessful(Boolean.TRUE)
                .providerStatus(providerStatus)
                .terminalConfirmed(Boolean.TRUE)
                .build();
        }
        String webhookId = context.getHeader(KlingConstants.CALLBACK_HEADER_ID);
        WebhookLease lease = acquireWebhook(webhookId);
        if (lease.completed()) {
            log.info("Kling callback duplicate, taskId={}", task.getId());
            return KlingCallbackResult.ACCEPTED;
        }
        if (!lease.acquired()) {
            log.info("Kling callback is already processing, taskId={}", task.getId());
            return KlingCallbackResult.RETRYABLE_INTERNAL;
        }
        try {
            if (!KlingStatusMapper.isTerminal(providerStatus)) {
                taskDispatchService.markUpstreamProgress(task);
            } else {
                boolean completed = taskCompletionService.completeTask(task.getId(), result);
                log.info("Kling callback terminal handling complete, taskId={}, status={}, completed={}",
                    task.getId(), normalized, completed);
            }
            markWebhookCompleted(lease.key());
            return KlingCallbackResult.ACCEPTED;
        } finally {
            releaseWebhookLease(lease);
        }
    }

    private String resolveProviderWebhookSecret() {
        LambdaQueryWrapper<SakuraAiProvider> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SakuraAiProvider::getProviderCode, SakuraAiProvider::getApiSecret)
            .eq(SakuraAiProvider::getProviderCode, KlingConstants.PROVIDER_CODE);
        List<SakuraAiProvider> providers = sakuraAiProviderService.list(wrapper);
        if (providers == null || providers.size() != 1) {
            return null;
        }
        SakuraAiProvider provider = providers.get(0);
        if (!Objects.equals(KlingConstants.PROVIDER_CODE,
            StrUtil.trimToEmpty(provider.getProviderCode()).toLowerCase(Locale.ROOT))
            || !KlingCallbackSignatureUtil.hasValidSecret(provider.getApiSecret())) {
            return null;
        }
        return provider.getApiSecret();
    }

    /** 精简定位同一上游任务号的候选任务，后续必须逐项使用其实际账号密钥验签。 */
    private List<SakuraMediaTask> findTaskCandidates(String providerTaskId) {
        LambdaQueryWrapper<SakuraMediaTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SakuraMediaTask::getId, SakuraMediaTask::getModelName, SakuraMediaTask::getProtocol,
                SakuraMediaTask::getProviderTaskId, SakuraMediaTask::getStatus, SakuraMediaTask::getUserId)
            .eq(SakuraMediaTask::getProviderTaskId, providerTaskId)
            .eq(SakuraMediaTask::getProtocol, KlingConstants.PROTOCOL_VIDEO)
            .in(SakuraMediaTask::getStatus, MediaTaskStatus.WAIT_CALLBACK.name(), MediaTaskStatus.WAIT_POLL.name(),
                MediaTaskStatus.PROCESSING.name(), MediaTaskStatus.SUCCEEDED.name(), MediaTaskStatus.FAILED.name())
            .orderByDesc(SakuraMediaTask::getId)
            .last("LIMIT " + TASK_CANDIDATE_LIMIT);
        List<SakuraMediaTask> tasks = sakuraMediaTaskService.list(wrapper);
        return tasks == null ? List.of() : tasks;
    }

    /** 从候选任务中找出唯一能用任务实际 Kling 账号密钥通过验签的任务。 */
    private SakuraMediaTask authenticateTask(List<SakuraMediaTask> candidates, String rawBody,
                                          KlingCallbackContext context) {
        SakuraMediaTask authenticated = null;
        for (SakuraMediaTask task : candidates) {
            AiModelConfigVo config = resolveModel(task);
            if (!isKling(config)
                || StrUtil.isBlank(KlingCallbackSupport.resolveCallbackUrlForSubmission(config))
                || !verify(rawBody, context, config.getApiSecret())) {
                continue;
            }
            if (authenticated != null) {
                log.warn("Kling callback matched multiple task credentials, providerTaskId={}", task.getProviderTaskId());
                return null;
            }
            authenticated = task;
        }
        return authenticated;
    }

    private boolean isTerminalTask(SakuraMediaTask task) {
        return Objects.equals(MediaTaskStatus.SUCCEEDED.name(), task.getStatus())
            || Objects.equals(MediaTaskStatus.FAILED.name(), task.getStatus());
    }

    private AiModelConfigVo resolveModel(SakuraMediaTask task) {
        try {
            return aiModelConfigService.selectByModelCodeForUser(task.getModelName(), task.getUserId());
        } catch (Exception ex) {
            log.warn("Kling callback cannot resolve model, taskId={}, error={}", task.getId(), ex.getClass().getSimpleName());
            return null;
        }
    }

    private boolean isKling(AiModelConfigVo config) {
        return config != null && Objects.equals(KlingConstants.PROVIDER_CODE,
            StrUtil.trimToEmpty(config.getProviderCode()).toLowerCase(Locale.ROOT));
    }

    private boolean verify(String rawBody, KlingCallbackContext context, String secrets) {
        return context != null && KlingCallbackSignatureUtil.verify(
            context.getHeader(KlingConstants.CALLBACK_HEADER_ID),
            context.getHeader(KlingConstants.CALLBACK_HEADER_TIMESTAMP),
            context.getHeader(KlingConstants.CALLBACK_HEADER_SIGNATURE), rawBody, secrets);
    }

    private WebhookLease acquireWebhook(String webhookId) {
        if (StrUtil.isBlank(webhookId)) {
            return WebhookLease.notAcquired();
        }
        String key = IDEMPOTENCY_PREFIX + DigestUtil.sha256Hex(webhookId);
        if (COMPLETED.equals(redisCache.getCacheObject(key))) {
            return WebhookLease.alreadyCompleted(key);
        }
        String token = "PROCESSING:" + UUID.randomUUID();
        boolean acquired = redisCache.setCacheObjectIfAbsent(
            key, token, PROCESSING_TTL_MINUTES, TimeUnit.MINUTES);
        if (!acquired && COMPLETED.equals(redisCache.getCacheObject(key))) {
            return WebhookLease.alreadyCompleted(key);
        }
        return acquired ? WebhookLease.acquired(key, token) : WebhookLease.notAcquired();
    }

    private void markWebhookCompleted(String key) {
        redisCache.setCacheObject(key, COMPLETED, IDEMPOTENCY_TTL_DAYS, TimeUnit.DAYS);
    }

    private void releaseWebhookLease(WebhookLease lease) {
        if (!lease.acquired()) {
            return;
        }
        try {
            redisCache.deleteObjectIfValueEquals(lease.key(), lease.token());
        } catch (Exception ex) {
            log.warn("Kling callback processing lease release failed, key={}", lease.key());
        }
    }

    private VideoOutput findVideoOutput(JsonNode root) {
        JsonNode outputs = root == null ? null : root.path("outputs");
        if (outputs != null && outputs.isArray()) {
            for (JsonNode item : outputs) {
                if ("video".equalsIgnoreCase(item.path("type").asText())) {
                    return new VideoOutput(item.path("url").asText(null), duration(item.path("duration").asText(null)));
                }
            }
        }
        JsonNode legacyVideos = root == null ? null : root.path("task_result").path("videos");
        if (legacyVideos != null && legacyVideos.isArray() && !legacyVideos.isEmpty()) {
            JsonNode item = legacyVideos.get(0);
            return new VideoOutput(item.path("url").asText(null), duration(item.path("duration").asText(null)));
        }
        return new VideoOutput(null, null);
    }

    private String firstText(JsonNode root, String... fields) {
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            String value = root.path(field).asText(null);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer duration(String value) {
        try {
            return StrUtil.isBlank(value) ? null
                : new BigDecimal(value.trim()).setScale(0, RoundingMode.CEILING).intValueExact();
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeFailure(String message) {
        String normalized = StrUtil.trimToEmpty(message).toLowerCase();
        return normalized.contains("content") || normalized.contains("policy") || normalized.contains("safety")
            || normalized.contains("risk") ? "生成内容未通过安全校验" : "上游任务执行失败";
    }

    private record VideoOutput(String url, Integer durationSeconds) {
    }

    private record WebhookLease(String key, String token, boolean acquired, boolean completed) {
        private static WebhookLease acquired(String key, String token) {
            return new WebhookLease(key, token, true, false);
        }

        private static WebhookLease alreadyCompleted(String key) {
            return new WebhookLease(key, null, false, true);
        }

        private static WebhookLease notAcquired() {
            return new WebhookLease(null, null, false, false);
        }
    }
}
