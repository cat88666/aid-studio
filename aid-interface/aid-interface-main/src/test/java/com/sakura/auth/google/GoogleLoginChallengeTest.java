package com.sakura.auth.google;

import com.sakura.common.core.redis.RedisCache;
import com.sakura.common.exception.ServiceException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleLoginChallengeTest {
    GoogleLoginConfig config;
    RedisCache redis;
    GoogleLoginChallenge challenge;
    MockHttpServletRequest request;
    String browser = "b".repeat(43), nonce = "n".repeat(43);
    @BeforeEach void setup() {
        config = new GoogleLoginConfig();
        ReflectionTestUtils.setField(config, "enabled", true);
        ReflectionTestUtils.setField(config, "clientId", "test.apps.googleusercontent.com");
        ReflectionTestUtils.setField(config, "origin", "https://thdrhdf.site");
        redis = mock(RedisCache.class);
        challenge = new GoogleLoginChallenge(config, redis);
        request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://thdrhdf.site");
        request.setCookies(new Cookie("__Host-sakura-google", browser));
    }
    @Test void disabledAndForeignOriginNeverTouchRedis() {
        ReflectionTestUtils.setField(config, "enabled", false);
        assertThrows(ServiceException.class, () -> challenge.issue(request, new MockHttpServletResponse()));
        ReflectionTestUtils.setField(config, "enabled", true);
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://evil.example");
        assertThrows(ServiceException.class, () -> challenge.issue(request, new MockHttpServletResponse()));
        verifyNoInteractions(redis);
    }
    @Test void browserCookieAndMatchingNonceRequired() {
        when(redis.getCacheObject(anyString())).thenReturn(nonce);
        assertDoesNotThrow(() -> challenge.validate(request, nonce));
        assertThrows(ServiceException.class, () -> challenge.validate(request, "x".repeat(43)));
        request.setCookies();
        assertThrows(ServiceException.class, () -> challenge.validate(request, nonce));
    }
    @Test void nonceOnlyConsumedOnce() {
        when(redis.getCacheObject(anyString())).thenReturn(nonce);
        when(redis.deleteObjectIfValueEquals(anyString(), eq(nonce))).thenReturn(true, false);
        challenge.consume(request, nonce);
        assertThrows(ServiceException.class, () -> challenge.consume(request, nonce));
    }
    @Test void issueUsesSecureCookieShortTtlAndRateLimit() {
        when(redis.setCacheObjectIfAbsent(anyString(), eq("1"), eq(2L), eq(TimeUnit.SECONDS))).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        var result = challenge.issue(request, response);
        assertEquals(43, result.get("nonce").toString().length());
        assertEquals(300, result.get("expiresIn"));
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("SameSite=Strict"));
        verify(redis).setCacheObject(anyString(), eq(result.get("nonce")), eq(5), eq(TimeUnit.MINUTES));
    }
}
