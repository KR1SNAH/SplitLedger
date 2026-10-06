package com.splitledger.auth;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure) {

    public AuthProperties {
        if (jwtSecret == null || Base64.getDecoder().decode(jwtSecret).length < 32) {
            throw new IllegalArgumentException("app.auth.jwt-secret must be a Base64-encoded key of at least 32 bytes");
        }
    }

    public SecretKey signingKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(jwtSecret), "HmacSHA256");
    }
}
