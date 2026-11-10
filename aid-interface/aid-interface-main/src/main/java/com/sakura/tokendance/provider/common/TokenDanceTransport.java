package com.sakura.tokendance.provider.common;

import com.sakura.domain.vo.AiModelConfigVo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/** TokenDance 生成网关传输层；Portal API 不能通过本接口调用。 */
public interface TokenDanceTransport
{
    /**
     * Validate all local transport configuration that can fail before a request is written.
     * Implementations should keep this method side-effect free so callers can run it before
     * freezing funds or marking a provider call as started.
     */
    default void validateConfiguration(AiModelConfigVo config, String relativePath,
            Map<String, String> protocolHeaders)
    {
        // Compatibility default for custom/test transports. The production transport performs
        // the full endpoint, timeout and header validation.
    }

    default TokenDanceHttpResponse exchange(String method, AiModelConfigVo config,
            String relativePath, String body) throws IOException
    {
        return exchange(method, config, relativePath,
                body == null ? null : body.getBytes(StandardCharsets.UTF_8), Collections.emptyMap());
    }

    default TokenDanceHttpResponse exchange(String method, AiModelConfigVo config,
            String relativePath, byte[] body) throws IOException
    {
        return exchange(method, config, relativePath, body, Collections.emptyMap());
    }

    TokenDanceHttpResponse exchange(String method, AiModelConfigVo config,
            String relativePath, byte[] body, Map<String, String> protocolHeaders) throws IOException;

    default TokenDanceHttpResponse exchangeBounded(String method, AiModelConfigVo config,
            String relativePath, byte[] body, Map<String, String> protocolHeaders,
            int maxResponseBytes) throws IOException
    {
        return exchange(method, config, relativePath, body, protocolHeaders);
    }

    TokenDanceHttpResponse exchangeMultipart(AiModelConfigVo config,
            String relativePath, byte[] body, String boundary) throws IOException;

    TokenDanceHttpResponse exchangeStream(String method, AiModelConfigVo config,
            String relativePath, byte[] body, Map<String, String> protocolHeaders,
            TokenDanceStreamHandler handler) throws IOException;
}
