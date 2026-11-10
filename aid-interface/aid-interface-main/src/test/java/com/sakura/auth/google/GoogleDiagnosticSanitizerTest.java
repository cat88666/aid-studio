package com.sakura.auth.google;

import com.sakura.sakura.mapper.SakuraAgentMapper;
import com.sakura.diagnostics.DiagnosticSanitizer;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleDiagnosticSanitizerTest {
    @Test void hidesNonceAndCredentialInStructuredAndLooseDiagnostics() {
        SakuraAgentMapper mapper = mock(SakuraAgentMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        DiagnosticSanitizer sanitizer = new DiagnosticSanitizer(mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(sanitizer, "loadedAt", System.currentTimeMillis());
        for (Object value : List.of(Map.of("googleNonce", "private-nonce", "credential", "private-token"),
                "googleNonce: private-nonce, credential: private-token", "https://example.com/?nonce=private-nonce")) {
            String cleaned = sanitizer.clean(value).toString();
            assertFalse(cleaned.contains("private-nonce"));
            assertFalse(cleaned.contains("private-token"));
        }
    }
}
