package com.sakura.media.provider;

import com.sakura.common.error.ErrorNormalizer;
import com.sakura.common.error.TaskErrorCode;
import com.sakura.common.error.TaskErrorResult;
import com.sakura.common.error.TaskErrorSnapshot;
import com.sakura.common.exception.ServiceException;
import com.sakura.tokendance.provider.common.TokenDanceResponseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** 统一保存文本上游调用证据并决定失败任务是否结算。 */
public final class TextFailureBillingPolicy {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OUTCOME_FIELD = "providerCallOutcome";
    private static final String HTTP_STATUS_FIELD = "providerHttpStatus";
    private static final String OUTCOME_NOT_SENT = "NOT_SENT";
    private static final String OUTCOME_REJECTED_WITHOUT_USAGE = "REJECTED_WITHOUT_USAGE";
    private static final String OUTCOME_USAGE_OBSERVED = "USAGE_OBSERVED";

    private TextFailureBillingPolicy() {
    }

    /** 构造上游 HTTP 最终拒绝异常，供流式回调保留机器可判定证据。 */
    public static ServiceException httpFailure(int httpStatus, String safeMessage,
                                               Map<String, Object> usage) {
        String snapshot = httpFailureSnapshot(httpStatus, safeMessage, usage);
        return new ServiceException(safeMessage, httpStatus).setTaskErrorJson(snapshot);
    }

    /** 构造上游 HTTP 失败快照；仅标准且语义明确的最终拒绝状态在无用量时退款。 */
    public static String httpFailureSnapshot(int httpStatus, String safeMessage,
                                             Map<String, Object> usage) {
        return httpFailureSnapshot(httpStatus, safeMessage, usage, null);
    }

    /** 在供应商专属错误快照上追加通用 HTTP/用量证据，不丢失恢复动作等扩展字段。 */
    public static String httpFailureSnapshot(int httpStatus, String safeMessage,
                                             Map<String, Object> usage, String preferredSnapshot) {
        TaskErrorResult error = ErrorNormalizer.classify(null, null, httpStatus, safeMessage);
        String snapshot = TaskErrorSnapshot.read(preferredSnapshot) == null
                ? TaskErrorSnapshot.write(error) : mergeErrorSnapshot(preferredSnapshot, error);
        if (ProviderUsageSupport.hasAnyProviderUsage(usage)) {
            return withObservedUsage(snapshot, usage);
        }
        if (isFinalHttpRejection(httpStatus)) {
            return withOutcome(snapshot, OUTCOME_REJECTED_WITHOUT_USAGE, httpStatus);
        }
        return snapshot;
    }

    /** 构造本地未发送请求的安全错误快照。 */
    public static String notSentSnapshot(String message) {
        return withOutcome(TaskErrorSnapshot.write(ErrorNormalizer.classifyByMessage(message)),
                OUTCOME_NOT_SENT, null);
    }

    /** 构造本地未发送请求异常。 */
    public static ServiceException notSent(String message) {
        return new ServiceException(message).setTaskErrorJson(notSentSnapshot(message));
    }

    /** 从异常链恢复完整调用证据，避免重新序列化错误时丢失扩展字段。 */
    public static String snapshotFrom(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof ServiceException serviceException
                    && TaskErrorSnapshot.read(serviceException.getTaskErrorJson()) != null) {
                return serviceException.getTaskErrorJson();
            }
            current = current.getCause();
        }
        return null;
    }

    /** 已观察到任何真实 token 字段时持久化用量证据，并撤销旧的无用量拒绝标记。 */
    public static String withObservedUsage(String snapshot, Map<String, Object> usage) {
        if (!ProviderUsageSupport.hasAnyProviderUsage(usage)) {
            return snapshot;
        }
        String migrated = TokenDanceResponseMapper.withObservedUsage(snapshot, usage);
        return withOutcome(migrated, OUTCOME_USAGE_OBSERVED, null);
    }

    /** 文本失败时是否结算；明确未发送/无用量拒绝退款，未知结果继续保守结算。 */
    public static boolean shouldSettle(boolean businessSucceeded, boolean providerCallStarted,
                                       String protocol, String snapshot,
                                       Map<String, Object> usage) {
        if (businessSucceeded) {
            return true;
        }
        TaskErrorResult error = TaskErrorSnapshot.read(snapshot);
        if (error != null && TaskErrorCode.RESULT_INVALID.name().equals(error.getErrorCode())) {
            return false;
        }
        if (ProviderUsageSupport.hasAnyProviderUsage(usage) || hasOutcome(snapshot, OUTCOME_USAGE_OBSERVED)) {
            return true;
        }
        return providerCallStarted && !isRejectedWithoutUsage(protocol, snapshot, usage);
    }

    /** 父任务聚合时判定子调用是否可能产生上游费用。 */
    public static boolean isBillableProviderCall(boolean providerCallStarted, String protocol,
                                                 String snapshot) {
        return shouldSettle(false, providerCallStarted, protocol, snapshot, null);
    }

    /** 合并标准错误展示字段，同时保留已有调用证据和供应商扩展字段。 */
    public static String mergeErrorSnapshot(String existingSnapshot, TaskErrorResult error) {
        JsonNode existing = readObject(existingSnapshot);
        JsonNode normalized = readObject(TaskErrorSnapshot.write(error));
        if (!(existing instanceof ObjectNode target) || !(normalized instanceof ObjectNode source)) {
            return TaskErrorSnapshot.write(error);
        }
        source.fields().forEachRemaining(entry -> target.set(entry.getKey(), entry.getValue()));
        return target.toString();
    }

    /** 判断错误快照是否确认请求未发送或被上游明确无用量拒绝。 */
    public static boolean isRejectedWithoutUsage(String protocol, String snapshot,
                                                 Map<String, Object> usage) {
        if (ProviderUsageSupport.hasAnyProviderUsage(usage) || hasOutcome(snapshot, OUTCOME_USAGE_OBSERVED)) {
            return false;
        }
        return hasOutcome(snapshot, OUTCOME_NOT_SENT)
                || hasOutcome(snapshot, OUTCOME_REJECTED_WITHOUT_USAGE)
                || TokenDanceResponseMapper.isConfirmedRejection(protocol, snapshot);
    }

    private static boolean isFinalHttpRejection(int httpStatus) {
        // 只接受标准且语义明确的最终拒绝；408、499 和供应商私有 4xx 可能代表断链或结果未知。
        return switch (httpStatus) {
            case 400, 401, 402, 403, 404, 405, 406, 407, 409, 410, 411, 412,
                    413, 414, 415, 416, 417, 421, 422, 423, 424, 425, 426,
                    428, 429, 431, 451 -> true;
            default -> false;
        };
    }

    private static boolean hasOutcome(String snapshot, String expected) {
        JsonNode node = readObject(snapshot);
        return node != null && expected.equals(node.path(OUTCOME_FIELD).asText());
    }

    private static String withOutcome(String snapshot, String outcome, Integer httpStatus) {
        JsonNode node = readObject(snapshot);
        if (!(node instanceof ObjectNode object)) {
            return snapshot;
        }
        object.put(OUTCOME_FIELD, outcome);
        if (httpStatus != null) {
            object.put(HTTP_STATUS_FIELD, httpStatus);
        }
        return object.toString();
    }

    private static JsonNode readObject(String snapshot) {
        if (snapshot == null || TaskErrorSnapshot.read(snapshot) == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(snapshot);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
