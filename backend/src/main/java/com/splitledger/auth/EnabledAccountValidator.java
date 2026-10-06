package com.splitledger.auth;

import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.splitledger.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Rejects access tokens whose account has been disabled (or no longer exists), so disabling a
 * user takes effect on their very next request instead of when their access token expires.
 * <p>
 * The trade-off: one primary-key lookup per authenticated request, which makes the JWTs not
 * purely stateless. For this app, revoking access immediately is worth it.
 */
@RequiredArgsConstructor
public class EnabledAccountValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error DISABLED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "This account has been disabled", null);

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Invalid subject", null));
        }
        return userRepository.existsByIdAndEnabledTrue(userId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(DISABLED);
    }
}
