package com.sakura.tokendance.provider.text;

import com.sakura.common.exception.ServiceException;
import com.sakura.domain.vo.AiModelConfigVo;
import com.sakura.media.dto.MediaTextGenerateRequest;
import com.sakura.media.provider.ProviderSubmitResult;
import com.sakura.media.provider.ProviderTaskResult;
import com.sakura.media.provider.ReasoningContentSanitizer;
import com.sakura.media.provider.TextOutputLimitResolver;
import com.sakura.media.provider.TextFailureBillingPolicy;
import com.sakura.media.provider.TextProviderClient;
import com.sakura.media.provider.TextStreamCallbacks;
import com.sakura.tokendance.provider.common.TokenDanceHttpResponse;
import com.sakura.tokendance.provider.common.TokenDanceEndpoints;
import com.sakura.tokendance.provider.common.TokenDancePayloadSupport;
import com.sakura.tokendance.provider.common.TokenDanceProtocols;
import com.sakura.tokendance.provider.common.TokenDanceResponseMapper;
import com.sakura.tokendance.provider.common.TokenDanceTransport;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/** TokenDance 三种文本协议共享的传输与 SSE 生命周期。 */
abstract class AbstractTokenDanceTextProviderClient implements TextProviderClient
{
    private final TokenDanceTransport transport;
    private final String protocol;
    private final String endpoint;

    protected AbstractTokenDanceTextProviderClient(TokenDanceTransport transport,
            String protocol, String endpoint)
    {
        this.transport = transport;
        this.protocol = protocol;
        this.endpoint = endpoint;
    }

    @Override
    public final String protocol()
    {
        return protocol;
    }

    @Override
    public final boolean supportsModel(String modelName)
    {
        return false;
    }

    @Override
    public final void validateRequest(AiModelConfigVo modelConfig, MediaTextGenerateRequest request)
    {
        requireRequest(modelConfig, request);
        com.sakura.model.definition.ModelConfiguredRequestBody.applyJson(modelConfig, buildBody(modelConfig, request, false), request);
    }

    @Override
    public final void validateProviderConfiguration(AiModelConfigVo modelConfig,
            MediaTextGenerateRequest request)
    {
        try
        {
            TokenDancePayloadSupport.requireModel(modelConfig, Collections.singleton(protocol));
            transport.validateConfiguration(modelConfig,
                    TokenDanceEndpoints.submitPath(modelConfig, endpoint), protocolHeaders());
        }
        catch (RuntimeException error)
        {
            throw TextFailureBillingPolicy.notSent(
                    error.getMessage() == null ? "模型配置不完整" : error.getMessage());
        }
    }

    @Override
    public final void streamChat(AiModelConfigVo modelConfig, MediaTextGenerateRequest request,
            TextStreamCallbacks callbacks) throws IOException
    {
        validateProviderConfiguration(modelConfig, request);
        requireRequest(modelConfig, request);
        TextOutputLimitResolver.normalize(request, modelConfig);
        validateRequest(modelConfig, request);
        byte[] body = com.sakura.model.definition.ModelConfiguredRequestBody.applyJson(modelConfig, buildBody(modelConfig, request, true), request).getBytes(StandardCharsets.UTF_8);
        java.util.concurrent.atomic.AtomicBoolean terminalError = new java.util.concurrent.atomic.AtomicBoolean();
        StreamState state = new StreamState(callbacks, TokenDanceToolMessages.requested(request));
        TokenDanceHttpResponse response = transport.exchangeStream("POST", modelConfig, com.sakura.tokendance.provider.common.TokenDanceEndpoints.submitPath(modelConfig, endpoint),
                body, protocolHeaders(), (stream, status, contentType, recoveryAction) -> {
                    callbacks.onResponseBody(stream);
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8)))
                    {
                        StringBuilder frame = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null)
                        {
                            if (line.startsWith("\uFEFF")) line = line.substring(1);
                            if (line.startsWith("data:")) {
                                if (!frame.isEmpty()) frame.append('\n');
                                frame.append(line.substring(5).stripLeading());
                                if (frame.length() > 1_000_000) throw new ServiceException("文本事件过大");
                            } else if (line.isEmpty() && !frame.isEmpty()) {
                                boolean stop = state.accept(frame.toString(), recoveryAction, status);
                                frame.setLength(0);
                                if (stop) break;
                            }
                        }
                        if (!frame.isEmpty()) state.accept(frame.toString(), recoveryAction, status);
                        terminalError.set(state.failed);
                    }
                });
        if (terminalError.get()) return;
        if (!response.isSuccessful())
        {
            String audit = TokenDanceResponseMapper.auditBody(response);
            JsonNode root = TokenDanceResponseMapper.readTree(response.bodyUtf8());
            Map<String, Object> usage = TokenDanceResponseMapper.usage(root);
            if (usage != null) callbacks.onUsage(usage);
            String message = TokenDanceResponseMapper.errorMessage(response, "上游请求失败");
            String snapshot = TextFailureBillingPolicy.httpFailureSnapshot(
                    response.getStatusCode(), message, usage,
                    TokenDanceResponseMapper.recoverySnapshot(protocol, audit));
            callbacks.onError(message, new ServiceException(message, response.getStatusCode())
                    .setTaskErrorJson(snapshot));
            return;
        }
        if (!state.completed) {
            callbacks.onError("文本响应未完成", new ServiceException("文本响应未完成"));
            return;
        }
        if (state.incomplete) {
            callbacks.onError("文本响应未完成", new ServiceException("文本响应未完成"));
            return;
        }
        if (state.tools != null) {
            var message = state.tools.finish();
            if (message != null) {
                if (!state.toolTurn) throw new ServiceException("上游返回未请求工具");
                callbacks.onToolMessage(message);
            }
        }
        callbacks.onComplete();
    }

    @Override
    public final ProviderSubmitResult chatSync(AiModelConfigVo modelConfig,
            MediaTextGenerateRequest request)
    {
        validateProviderConfiguration(modelConfig, request);
        requireRequest(modelConfig, request);
        TextOutputLimitResolver.normalize(request, modelConfig);
        validateRequest(modelConfig, request);
        try
        {
            TokenDanceHttpResponse response = transport.exchange("POST", modelConfig, com.sakura.tokendance.provider.common.TokenDanceEndpoints.submitPath(modelConfig, endpoint),
                    com.sakura.model.definition.ModelConfiguredRequestBody.applyJson(modelConfig, buildBody(modelConfig, request, false), request).getBytes(StandardCharsets.UTF_8), protocolHeaders());
            String audit = ReasoningContentSanitizer.sanitizeJson(
                    TokenDanceResponseMapper.auditBody(response));
            JsonNode root = TokenDanceResponseMapper.readTree(response.bodyUtf8());
            if (!response.isSuccessful() || root == null || isErrorEvent(root)
                    || "failed".equals(root.path("status").asText()) || "incomplete".equals(root.path("status").asText()))
            {
                Map<String, Object> usage = TokenDanceResponseMapper.usage(root);
                String message = TokenDanceResponseMapper.errorMessage(response, "上游请求失败");
                String snapshot = TextFailureBillingPolicy.httpFailureSnapshot(
                        response.getStatusCode(), message, usage,
                        TokenDanceResponseMapper.recoverySnapshot(protocol, audit));
                return ProviderSubmitResult.builder().rawResponse(audit)
                        .errorDetailJson(snapshot).usage(usage).build();
            }
            if (incompleteOutput(root)) return ProviderSubmitResult.builder()
                    .rawResponse("文本响应未完成").usage(TokenDanceResponseMapper.usage(root)).build();
            try {
                ProviderSubmitResult result = parseSync(response, audit);
                if (result.getToolMessage() != null && !TokenDanceToolMessages.requested(request))
                    throw new ServiceException("上游返回未请求工具");
                return result;
            } catch (RuntimeException exception) {
                // 响应已到达时，解析失败也必须保留官方用量，避免按未调用全额退款。
                return ProviderSubmitResult.builder().rawResponse("文本结果格式无效")
                        .usage(TokenDanceResponseMapper.usage(root)).build();
            }
        }
        catch (IOException exception)
        {
            return ProviderSubmitResult.builder().rawResponse("上游请求失败").build();
        }
    }

    @Override
    public final ProviderTaskResult query(AiModelConfigVo modelConfig, String providerTaskId)
    {
        return ProviderTaskResult.builder()
                .status("PROCESSING")
                .errorMessage("同步模型无需查询")
                .querySuccessful(Boolean.FALSE)
                .terminalConfirmed(Boolean.FALSE)
                .build();
    }

    protected abstract String buildBody(AiModelConfigVo modelConfig,
            MediaTextGenerateRequest request, boolean stream);

    protected abstract void acceptStreamEvent(JsonNode event, TextStreamCallbacks callbacks);

    protected abstract ProviderSubmitResult parseSync(TokenDanceHttpResponse response, String auditBody);

    protected Map<String, String> protocolHeaders()
    {
        return Collections.emptyMap();
    }

    private void requireRequest(AiModelConfigVo modelConfig, MediaTextGenerateRequest request)
    {
        if (modelConfig == null || !TokenDanceProtocols.isTokenDance(modelConfig.getProviderCode())
                || !TokenDanceProtocols.matches(protocol, modelConfig.getProtocol()))
        {
            throw new ServiceException("模型配置错误");
        }
        if (request == null)
        {
            throw new IllegalArgumentException("文本请求不能为空");
        }
        TokenDanceToolMessages.validate(modelConfig, request);
    }

    private final class StreamState {
        private final TextStreamCallbacks callbacks;
        private final TokenDanceToolMessages.Accumulator tools;
        private final boolean toolTurn;
        private boolean completed;
        private boolean failed;
        private boolean incomplete;
        private StreamState(TextStreamCallbacks callbacks, boolean toolTurn) {
            this.callbacks = callbacks;
            this.toolTurn = toolTurn;
            this.tools = toolTurn ? new TokenDanceToolMessages.Accumulator(protocol) : null;
        }
        private boolean accept(String data, String recoveryAction, int httpStatus) {
            callbacks.onSseDataLine(ReasoningContentSanitizer.sanitizeJson(data));
            if ("[DONE]".equals(data)) {
                if (com.sakura.tokendance.provider.common.TokenDanceProtocols.OPENAI_CHAT_COMPLETIONS.equals(protocol)) completed = true;
                return true;
            }
            JsonNode event = TokenDanceResponseMapper.readTree(data);
            if (event == null || !event.isObject()) throw new ServiceException("文本事件无效");
            if (isErrorEvent(event)) {
                Map<String, Object> usage = TokenDanceResponseMapper.usage(event);
                if (usage != null) callbacks.onUsage(usage);
                failed = true;
                TokenDanceHttpResponse error = new TokenDanceHttpResponse(httpStatus, "application/json",
                         data.getBytes(StandardCharsets.UTF_8), recoveryAction);
                String recoverySnapshot = TokenDanceResponseMapper.recoverySnapshot(
                        protocol, TokenDanceResponseMapper.auditBody(error));
                String message = TokenDanceResponseMapper.errorMessage(error, "上游请求失败");
                String snapshot = TextFailureBillingPolicy.httpFailureSnapshot(
                        httpStatus, message, usage, recoverySnapshot);
                callbacks.onError(message, new ServiceException(message, httpStatus)
                        .setTaskErrorJson(snapshot));
                return true;
            }
            acceptStreamEvent(event, callbacks);
            if (!toolTurn && (!event.path("choices").path(0).path("delta").path("tool_calls").isMissingNode()
                    && event.path("choices").path(0).path("delta").path("tool_calls").size() > 0
                    || "tool_use".equals(event.path("content_block").path("type").asText())
                    || "response.completed".equals(event.path("type").asText())
                        && TokenDanceToolMessages.sync(event.path("response"), protocol) != null))
                throw new ServiceException("上游返回未请求工具");
            if (tools != null) tools.accept(event);
            incomplete |= incompleteOutput(event);
            String type = event.path("type").asText();
            if ("response.completed".equals(type) && com.sakura.tokendance.provider.common.TokenDanceProtocols.OPENAI_RESPONSES.equals(protocol)
                    || "message_stop".equals(type) && com.sakura.tokendance.provider.common.TokenDanceProtocols.ANTHROPIC_MESSAGES.equals(protocol)) {
                completed = true;
                return true;
            }
            // Chat 的 finish_reason 之后可能还有独立 usage 帧，继续读取至 DONE 或 EOF。
            String finish = event.path("choices").path(0).path("finish_reason").asText();
            if (com.sakura.tokendance.provider.common.TokenDanceProtocols.OPENAI_CHAT_COMPLETIONS.equals(protocol)
                    && ("stop".equals(finish) || "tool_calls".equals(finish))) completed = true;
            return false;
        }
    }

    private static boolean isErrorEvent(JsonNode event)
    {
        String type = event.path("type").asText();
        return event.hasNonNull("error") || "error".equals(type)
                || "response.failed".equals(type) || "response.incomplete".equals(type)
                || event.path("response").hasNonNull("error")
                || "failed".equals(event.path("response").path("status").asText())
                || "incomplete".equals(event.path("response").path("status").asText());
    }

    private static boolean incompleteOutput(JsonNode root) {
        String finish = root.path("choices").path(0).path("finish_reason").asText();
        String stop = root.path("stop_reason").asText(root.path("delta").path("stop_reason").asText());
        return "length".equals(finish) || "content_filter".equals(finish)
                || "max_tokens".equals(stop) || "model_context_window_exceeded".equals(stop);
    }
}
