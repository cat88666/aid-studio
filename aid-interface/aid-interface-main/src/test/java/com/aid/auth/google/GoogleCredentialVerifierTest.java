package com.aid.auth.google;

import com.aid.common.exception.ServiceException;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleCredentialVerifierTest {
    GoogleCredentialVerifier service;
    KeyPair pair;
    long now = System.currentTimeMillis()/1000;
    GoogleIdToken.Payload payload;
    @BeforeEach void setup() throws Exception {
        GoogleLoginConfig config = new GoogleLoginConfig();
        ReflectionTestUtils.setField(config, "enabled", true);
        ReflectionTestUtils.setField(config, "clientId", "test.apps.googleusercontent.com");
        ReflectionTestUtils.setField(config, "origin", "https://thdrhdf.site");
        service = new GoogleCredentialVerifier(config);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); pair = generator.generateKeyPair();
        GooglePublicKeysManager keys = mock(GooglePublicKeysManager.class);
        when(keys.getPublicKeys()).thenReturn(List.of(pair.getPublic()));
        when(keys.getJsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(keys)
                .setAudience(List.of("test.apps.googleusercontent.com")).setAcceptableTimeSkewSeconds(0).build();
        ReflectionTestUtils.setField(service, "verifier", verifier);
        payload = new GoogleIdToken.Payload();
        payload.setIssuer("https://accounts.google.com"); payload.setAudience("test.apps.googleusercontent.com");
        payload.setIssuedAtTimeSeconds(now); payload.setExpirationTimeSeconds(now+300); payload.setSubject("google-sub");
        payload.setEmail("hello@gmail.com"); payload.setEmailVerified(true); payload.set("nonce", "nonce");
    }
    String token(PrivateKey key) throws Exception {
        return JsonWebSignature.signUsingRsaSha256(key, GsonFactory.getDefaultInstance(), new JsonWebSignature.Header().setAlgorithm("RS256"), payload);
    }
    @Test void acceptsSignedIdentityAndAuthoritativeEmail() throws Exception {
        assertEquals(new GoogleCredentialVerifier.Identity("google-sub", "hello@gmail.com"), service.verify(token(pair.getPrivate()), "nonce"));
    }
    @Test void refusesWrongAudienceIssuerExpirationAndNonce() throws Exception {
        payload.setAudience("other"); assertThrows(ServiceException.class, () -> service.verify(token(pair.getPrivate()), "nonce"));
        payload.setAudience("test.apps.googleusercontent.com"); payload.setIssuer("https://evil.example"); assertThrows(ServiceException.class, () -> service.verify(token(pair.getPrivate()), "nonce"));
        payload.setIssuer("accounts.google.com"); payload.setExpirationTimeSeconds(now-30); assertThrows(ServiceException.class, () -> service.verify(token(pair.getPrivate()), "nonce"));
        payload.setExpirationTimeSeconds(now+300); assertThrows(ServiceException.class, () -> service.verify(token(pair.getPrivate()), "other"));
    }
    @Test void refusesInvalidSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        assertThrows(ServiceException.class, () -> service.verify(token(generator.generateKeyPair().getPrivate()), "nonce"));
    }
    @Test void neverBindsExternalEmailAsRecoveryIdentity() throws Exception {
        payload.setEmail("hello@example.com");
        assertNull(service.verify(token(pair.getPrivate()), "nonce").verifiedEmail());
        payload.setEmailVerified(false); payload.setEmail("hello@gmail.com");
        assertNull(service.verify(token(pair.getPrivate()), "nonce").verifiedEmail());
    }
}
