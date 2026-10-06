package com.splitledger.auth;

import static com.splitledger.common.SecureTokens.hash;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;
import com.splitledger.common.SecureTokens;
import com.splitledger.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Opaque, rotating refresh tokens.
 * <p>
 * Every refresh revokes the presented token and issues a new one. If an already-revoked token is
 * presented again it has most likely been stolen, so every token for that user is revoked
 * (refresh token reuse detection).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;

    public record Rotation(User user, String newToken) {
    }

    @Transactional
    public String create(User user) {
        String rawToken = SecureTokens.generate();
        repository.save(new RefreshToken(user, hash(rawToken), Instant.now().plus(properties.refreshTokenTtl())));
        return rawToken;
    }

    // noRollbackFor: a detected reuse must still commit the mass revocation before the request is rejected.
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for user {}; revoking all sessions", token.getUser().getId());
            repository.revokeAllForUser(token.getUser().getId(), now);
            throw ApiException.unauthorized("Invalid refresh token");
        }
        if (token.isExpired(now)) {
            throw ApiException.unauthorized("Refresh token expired");
        }

        token.revoke(now);
        User user = token.getUser();
        if (!user.isEnabled()) {
            // Disabling revokes every session already; this covers a refresh racing the disable.
            throw ApiException.unauthorized("This account has been disabled");
        }
        return new Rotation(user, create(user));
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(Instant.now()));
    }

    /** Signs the user out everywhere. */
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }
}
