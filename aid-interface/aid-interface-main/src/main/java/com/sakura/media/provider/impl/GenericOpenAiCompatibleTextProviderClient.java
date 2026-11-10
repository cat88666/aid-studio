package com.sakura.media.provider.impl;

import cn.hutool.core.util.StrUtil;
import com.sakura.domain.vo.AiModelConfigVo;
import com.sakura.media.constants.OpenAiCompatibleConstants;
import com.sakura.media.dto.MediaTextGenerateRequest;
import com.sakura.media.provider.ModelCodeResolver;
import com.sakura.media.provider.OpenAiCompatiblePayloadResolver;
import com.sakura.media.provider.OpenAiStyleChatStream;
import com.sakura.media.provider.ProviderSubmitResult;
import com.sakura.media.provider.ProviderTaskResult;
import com.sakura.media.provider.TextChatOpenAiPayloadBuilder;
import com.sakura.media.provider.TextProviderClient;
import com.sakura.media.provider.TextOutputLimitResolver;
import com.sakura.media.provider.TextReasoningOptionsResolver;
import com.sakura.media.provider.TextStreamCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 通用 OpenAI 兼容文本 Provider：统一承载各家 OpenAI Chat Completions 兼容的文本模型接入。
 *
 * @author 视觉AID
 */
@Slf4j
@Component
public class GenericOpenAiCompatibleTextProviderClient implements TextProviderClient {

    @Override
    public String protocol() {
        return OpenAiCompatibleConstants.PROTOCOL_TEXT;
    }

    @Override
    public void validateProviderConfiguration(AiModelConfigVo modelConfig, MediaTextGenerateRequest request) {
        if (modelConfig == null) {
            throw com.sakura.media.provider.TextFailureBillingPolicy.notSent("模型未配置");
        }
        if (StringUtils.isBlank(modelConfig.getApiKey())) {
            throw com.sakura.media.provider.TextFailureBillingPolicy.notSent(
                    OpenAiCompatibleConstants.ERROR_API_KEY_EMPTY);
        }
        try {
            String url = OpenAiCompatiblePayloadResolver.buildApiUrl(modelConfig.getBaseUrl(),
                    resolveApiSuffix(modelConfig, request), modelConfig.getExtraQueryJson());
            if (StringUtils.isBlank(url)) {
                throw new IllegalArgumentException(OpenAiCompatibleConstants.ERROR_BASE_URL_EMPTY);
            }
            URI.create(url);
            OpenAiStyleChatStream.validateRequestConfiguration(url, modelConfig.getApiKey(),
                    modelConfig.getAuthHeader(), modelConfig.getAuthPrefix(),
                    OpenAiCompatiblePayloadResolver.parseExtraHeaders(modelConfig.getExtraHeadersJson()));
        } catch (IllegalArgumentException invalidUrl) {
            throw com.sakura.media.provider.TextFailureBillingPolicy.notSent("模型服务地址配置错误");
        }
        if (StringUtils.isBlank(resolveEffectiveModel(modelConfig, request))) {
            throw com.sakura.media.provider.TextFailureBillingPolicy.notSent("真实模型名称未配置");
        }
    }

    @Override
    public void streamChat(AiModelConfigVo modelConfig, MediaTextGenerateRequest request,
                           TextStreamCallbacks callbacks) throws IOException {
        TextOutputLimitResolver.normalize(request, modelConfig);
        String url = resolveUrlOrFail(modelConfig, request, callbacks, true);
        if (url == null) {
            return;
        }
        String apiKey = modelConfig != null ? modelConfig.getApiKey() : null;
        if (StringUtils.isBlank(apiKey)) {
            log.error("OpenAI 兼容流式: apiKey 为空, providerCode={}, modelCode={}",
                    modelConfig == null ? null : modelConfig.getProviderCode(),
                    modelConfig == null ? null : modelConfig.getModelCode());
            callbacks.onError(OpenAiCompatibleConstants.ERROR_API_KEY_EMPTY,
                    com.sakura.media.provider.TextFailureBillingPolicy.notSent(
                            OpenAiCompatibleConstants.ERROR_API_KEY_EMPTY));
            return;
        }
        String model = resolveEffectiveModel(modelConfig, request);
        List<Map<String, Object>> messages = TextChatOpenAiPayloadBuilder.buildMessageMaps(modelConfig, request, supportsToolMessages());
        Map<String, Object> mergedOptions = OpenAiCompatiblePayloadResolver.mergeExtraBody(
                modelConfig.getExtraBodyJson(), modelConfig.getModelExtraBodyJson(), request.getOptions());
        mergedOptions = TextReasoningOptionsResolver.resolveOpenAiCompatible(modelConfig, request, mergedOptions);
        mergedOptions = normalizeProviderOptions(modelConfig, request, mergedOptions);
        // 结构化输出必须由业务显式开启，不能仅因对话中出现 JSON 字样而改变回复协议。
        mergedOptions = com.sakura.media.provider.StructuredOutputSupport
                .applyJsonModeIfSupported(modelConfig, messages, mergedOptions);
        String body = com.sakura.model.definition.ModelConfiguredRequestBody.applyJson(modelConfig, buildBody(model, messages, true, mergedOptions, request), request);
        Map<String, String> extraHeaders = OpenAiCompatiblePayloadResolver.parseExtraHeaders(
                modelConfig.getExtraHeadersJson());
        log.info("OpenAI 兼容流式提交, providerCode={}, model={}, messagesSize={}, extraOptionsKeys={}",
                modelConfig.getProviderCode(), model, messages.size(),
                mergedOptions == null ? 0 : mergedOptions.size());
        OpenAiStyleChatStream.postSseStream(url, apiKey,
                modelConfig.getAuthHeader(), modelConfig.getAuthPrefix(), extraHeaders,
                body, callbacks, supportsToolMessages() && com.sakura.tokendance.provider.text.TokenDanceToolMessages.requested(request), supportsTextCompletions());
    }

    @Override
    public ProviderSubmitResult chatSync(AiModelConfigVo modelConfig, MediaTextGenerateRequest request) {
        TextOutputLimitResolver.normalize(request, modelConfig);
        String url = OpenAiCompatiblePayloadResolver.buildApiUrl(
                modelConfig != null ? modelConfig.getBaseUrl() : null,
                resolveApiSuffix(modelConfig, request),
                modelConfig != null ? modelConfig.getExtraQueryJson() : null);
        if (url == null) {
            log.error("OpenAI 兼容非流式: base_url 或 api_suffix 缺失, providerCode={}, modelCode={}",
                    modelConfig == null ? null : modelConfig.getProviderCode(),
                    modelConfig == null ? null : modelConfig.getModelCode());
            return ProviderSubmitResult.builder()
                    .rawResponse(OpenAiCompatibleConstants.ERROR_BASE_URL_EMPTY)
                    .errorDetailJson(com.sakura.media.provider.TextFailureBillingPolicy.notSentSnapshot(
                            OpenAiCompatibleConstants.ERROR_BASE_URL_EMPTY))
                    .build();
        }
        String apiKey = modelConfig.getApiKey();
        if (StringUtils.isBlank(apiKey)) {
            return ProviderSubmitResult.builder()
                    .rawResponse(OpenAiCompatibleConstants.ERROR_API_KEY_EMPTY)
                    .errorDetailJson(com.sakura.media.provider.TextFailureBillingPolicy.notSentSnapshot(
                            OpenAiCompatibleConstants.ERROR_API_KEY_EMPTY))
                    .build();
        }
        String model = resolveEffectiveModel(modelConfig, request);
        List<Map<String, Object>> messages = TextChatOpenAiPayloadBuilder.buildMessageMaps(modelConfig, request, supportsToolMessages());
        Map<String, Object> mergedOptions = OpenAiCompatiblePayloadResolver.mergeExtraBody(
                modelConfig.getExtraBodyJson(), modelConfig.getModelExtraBodyJson(), request.getOptions());
        mergedOptions = TextReasoningOptionsResolver.resolveOpenAiCompatible(modelConfig, request, mergedOptions);
        mergedOptions = normalizeProviderOptions(modelConfig, request, mergedOptions);
        // 结构化输出必须由业务显式开启，不能仅因对话中出现 JSON 字样而改变回复协议。
        mergedOptions = com.sakura.media.provider.StructuredOutputSupport
                .applyJsonModeIfSupported(modelConfig, messages, mergedOptions);
        String body = com.sakura.model.definition.ModelConfiguredRequestBody.applyJson(modelConfig, buildBody(model, messages, false, mergedOptions, request), request);
        Map<String, String> extraHeaders = OpenAiCompatiblePayloadResolver.parseExtraHeaders(
                modelConfig.getExtraHeadersJson());
        log.info("OpenAI 兼容非流式(NON_STREAM), providerCode={}, model={}, messagesSize={}",
                modelConfig.getProviderCode(), model, messages.size());
        return OpenAiStyleChatStream.postJsonSync(url, apiKey,
                modelConfig.getAuthHeader(), modelConfig.getAuthPrefix(), extraHeaders, body,
                supportsToolMessages() && com.sakura.tokendance.provider.text.TokenDanceToolMessages.requested(request), supportsTextCompletions());
    }

    protected boolean supportsToolMessages() {
        return false;
    }

    protected boolean supportsTextCompletions() {
        return false;
    }

    protected String buildBody(String model, List<Map<String, Object>> messages, boolean stream,
            Map<String, Object> options, MediaTextGenerateRequest request) {
        return TextChatOpenAiPayloadBuilder.buildChatCompletionsJsonBody(model, messages, stream, options);
    }

    protected Map<String, Object> normalizeProviderOptions(AiModelConfigVo modelConfig,
            MediaTextGenerateRequest request, Map<String, Object> options) {
        return options;
    }

    /** 允许特定协议按请求语义选择正式或实验端点。 */
    protected String resolveApiSuffix(AiModelConfigVo modelConfig, MediaTextGenerateRequest request) {
        return modelConfig == null ? null : modelConfig.getApiSuffix();
    }

    @Override
    public ProviderTaskResult query(AiModelConfigVo modelConfig, String providerTaskId) {
        // 文本模型同步返回，无官方异步任务状态。
        return ProviderTaskResult.builder()
                .status("PROCESSING")
                .errorMessage("同步模型无上游查询状态")
                .querySuccessful(Boolean.FALSE)
                .terminalConfirmed(Boolean.FALSE)
                .build();
    }

    /**
     * 计算实际下发上游的模型名：经 {@link ModelCodeResolver} 解析（real_model_code 解耦展示码），最后兜底常量。
     */
    protected String resolveEffectiveModel(AiModelConfigVo modelConfig, MediaTextGenerateRequest request) {
        // 解析真实上游模型名：展示码 model_code 与真实模型名 real_model_code 解耦
        String resolved = ModelCodeResolver.resolveUpstreamModel(modelConfig,
                request == null ? null : request.getModelName());
        if (StrUtil.isNotBlank(resolved)) {
            return resolved;
        }
        return OpenAiCompatibleConstants.DEFAULT_TEXT_MODEL;
    }

    /**
     * 流式入口的 URL 解析：缺配置时通过 callbacks 上报错误而不是抛异常，
     * 避免上层 SSE sink 收到未归一化的栈消息。
     */
    private String resolveUrlOrFail(AiModelConfigVo modelConfig, MediaTextGenerateRequest request,
                                    TextStreamCallbacks callbacks, boolean isStream) {
        String baseUrl = modelConfig != null ? modelConfig.getBaseUrl() : null;
        String apiSuffix = resolveApiSuffix(modelConfig, request);
        if (StringUtils.isBlank(baseUrl)) {
            log.error("OpenAI 兼容{}: base_url 为空, providerCode={}",
                    isStream ? "流式" : "非流式", modelConfig == null ? null : modelConfig.getProviderCode());
            callbacks.onError(OpenAiCompatibleConstants.ERROR_BASE_URL_EMPTY,
                    com.sakura.media.provider.TextFailureBillingPolicy.notSent(
                            OpenAiCompatibleConstants.ERROR_BASE_URL_EMPTY));
            return null;
        }
        if (StringUtils.isBlank(apiSuffix)) {
            log.error("OpenAI 兼容{}: api_suffix 为空, modelCode={}",
                    isStream ? "流式" : "非流式", modelConfig == null ? null : modelConfig.getModelCode());
            callbacks.onError(OpenAiCompatibleConstants.ERROR_API_SUFFIX_EMPTY,
                    com.sakura.media.provider.TextFailureBillingPolicy.notSent(
                            OpenAiCompatibleConstants.ERROR_API_SUFFIX_EMPTY));
            return null;
        }
        return OpenAiCompatiblePayloadResolver.buildApiUrl(baseUrl, apiSuffix,
                modelConfig.getExtraQueryJson());
    }
}
