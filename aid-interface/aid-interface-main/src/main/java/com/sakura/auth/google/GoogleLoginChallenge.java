package com.sakura.auth.google;

import cn.hutool.crypto.digest.DigestUtil;
import com.sakura.common.core.redis.RedisCache;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.ip.IpUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** A short-lived nonce bound to a same-origin, HttpOnly browser cookie. */
@Service
@RequiredArgsConstructor
public class GoogleLoginChallenge {
    private static final String COOKIE = "__Host-sakura-google";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GoogleLoginConfig config;
    private final RedisCache redis;

    public void requireOrigin(HttpServletRequest request) {
        if (!config.isReady()) throw new ServiceException("Google登录未开启");
        if (!config.getOrigin().equals(request.getHeader("Origin"))) throw new ServiceException("Google登录请求来源无效");
    }

    public Map<String, Object> issue(HttpServletRequest request, HttpServletResponse response) {
        requireOrigin(request);
        String rateKey = "auth:google:rate:" + DigestUtil.sha256Hex(IpUtils.getIpAddr(request));
        if (!redis.setCacheObjectIfAbsent(rateKey, "1", 2, TimeUnit.SECONDS)) {
            throw new ServiceException("请求过于频繁，请稍后重试");
        }
        String browser = browser(request);
        if (browser == null) browser = random();
        String nonce = random();
        redis.setCacheObject(key(browser), nonce, 5, TimeUnit.MINUTES);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, browser)
                .httpOnly(true).secure(true).sameSite("Strict").path("/").maxAge(300).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return Map.of("nonce", nonce, "expiresIn", 300);
    }

    public void validate(HttpServletRequest request, String nonce) {
        requireOrigin(request);
        String browser = browser(request);
        if (browser == null || nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")
                || !nonce.equals(redis.getCacheObject(key(browser)))) {
            throw new ServiceException("Google登录已过期，请重试");
        }
    }

    public void consume(HttpServletRequest request, String nonce) {
        validate(request, nonce);
        if (!redis.deleteObjectIfValueEquals(key(browser(request)), nonce)) {
            throw new ServiceException("Google登录已过期，请重试");
        }
    }

    private String browser(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        }
        return null;
    }
    private String key(String browser) { return "auth:google:nonce:" + DigestUtil.sha256Hex(browser); }
    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
