package com.sakura.compat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakura.common.utils.LegacyWebIdentifiers;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.lang.reflect.Type;

/** Runs after request deserialization and before response encryption; never modifies persistence objects. */
@ControllerAdvice(basePackages = "com.sakura")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyWebBodyAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;
    public LegacyWebBodyAdvice(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
                                          Type type, Class<? extends HttpMessageConverter<?>> converter) {
        JsonNode original = mapper.valueToTree(body);
        JsonNode normalized = LegacyWebIdentifiers.request(original.deepCopy());
        return original.equals(normalized) ? body : mapper.convertValue(normalized, mapper.constructType(type));
    }
    @Override public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }
    @Override public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!LegacyWebIdentifiers.isLegacyWeb(request.getURI().getPath(), request.getHeaders().getFirst("Referer"),
                request.getHeaders().getFirst("Host"))) return body;
        return LegacyWebIdentifiers.response(mapper.valueToTree(body));
    }
}
