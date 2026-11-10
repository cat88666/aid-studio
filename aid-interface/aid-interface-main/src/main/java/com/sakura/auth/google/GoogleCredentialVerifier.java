package com.sakura.auth.google;

import com.sakura.common.exception.ServiceException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Google library validates signature, audience, issuer and expiration; no tokeninfo shortcut. */
@Component
@RequiredArgsConstructor
public class GoogleCredentialVerifier {
    private final GoogleLoginConfig config;
    private volatile GoogleIdTokenVerifier verifier;

    public Identity verify(String credential, String nonce) {
        if (!config.isReady()) throw new ServiceException("Google登录未开启");
        if (credential == null || credential.length() > 16000 || credential.isBlank()) {
            throw new ServiceException("Google登录凭证无效");
        }
        try {
            GoogleIdToken token = verifier().verify(credential);
            if (token == null) throw new ServiceException("Google登录凭证无效");
            GoogleIdToken.Payload payload = token.getPayload();
            if (nonce == null || !nonce.equals(payload.get("nonce"))
                    || payload.getSubject() == null || payload.getSubject().isBlank()
                    || payload.getSubject().length() > 128) throw new ServiceException("Google登录凭证无效");
            // External Google account email may have changed owner: never make it a recovery identity.
            String email = payload.getEmail();
            boolean authoritative = Boolean.TRUE.equals(payload.getEmailVerified()) && email != null && email.length() <= 100
                    && (email.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com")
                    || (payload.getHostedDomain() != null && !payload.getHostedDomain().isBlank()));
            return new Identity(payload.getSubject(), authoritative ? email : null);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // Do not log the exception/token: parsers can include credential fragments in messages.
            throw new ServiceException("Google登录验证失败，请重试");
        }
    }

    private GoogleIdTokenVerifier verifier() {
        if (verifier == null) synchronized (this) {
            if (verifier == null) verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(List.of(config.getClientId())).setAcceptableTimeSkewSeconds(0).build();
        }
        return verifier;
    }
    public record Identity(String subject, String verifiedEmail) {}
}
