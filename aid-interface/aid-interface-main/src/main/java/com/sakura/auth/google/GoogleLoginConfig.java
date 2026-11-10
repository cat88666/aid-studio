package com.sakura.auth.google;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Explicitly opt-in; never infer authentication credentials from model API keys. */
@Getter
@Component
public class GoogleLoginConfig {
    @Value("${GOOGLE_LOGIN_ENABLED:false}")
    private boolean enabled;
    @Value("${GOOGLE_CLIENT_ID:}")
    private String clientId;
    @Value("${GOOGLE_LOGIN_ORIGIN:https://thdrhdf.site}")
    private String origin;

    public boolean isReady() {
        return enabled && clientId != null && clientId.matches("[A-Za-z0-9._-]+\\.apps\\.googleusercontent\\.com")
                && origin != null && origin.matches("https://[A-Za-z0-9.-]+(:[0-9]+)?");
    }
}
