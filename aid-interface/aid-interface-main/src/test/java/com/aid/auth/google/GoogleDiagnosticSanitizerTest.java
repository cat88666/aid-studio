package com.aid.auth.google;

import com.aid.aid.mapper.AidAgentMapper;
import com.aid.diagnostics.DiagnosticSanitizer;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleDiagnosticSanitizerTest {
    @Test void hidesNonceAndCredentialInStructuredAndLooseDiagnostics() {
        AidAgentMapper mapper = mock(AidAgentMapper.class);
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
