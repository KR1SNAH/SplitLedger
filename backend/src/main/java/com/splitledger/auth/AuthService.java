package com.splitledger.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.auth.dto.AuthResponse;
import com.splitledger.auth.dto.LoginRequest;
import com.splitledger.auth.dto.RegisterRequest;
import com.splitledger.common.ApiException;
import com.splitledger.user.Role;
import com.splitledger.user.User;
import com.splitledger.user.UserRepository;
import com.splitledger.user.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    static final String ACCOUNT_DISABLED = "This account has been disabled. Contact an administrator.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties properties;

    /** Result of a successful authentication: the response body plus the refresh token for the cookie. */
    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = userRepository.save(new User(
                email, passwordEncoder.encode(request.password()), request.displayName().trim(), Role.USER));
        return authenticate(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        // Checked after the password, so this doesn't reveal which accounts exist.
        if (!user.isEnabled()) {
            throw ApiException.forbidden(ACCOUNT_DISABLED);
        }
        return authenticate(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("Missing refresh token");
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return new AuthResult(buildResponse(rotation.user()), rotation.newToken());
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    private AuthResult authenticate(User user) {
        return new AuthResult(buildResponse(user), refreshTokenService.create(user));
    }

    private AuthResponse buildResponse(User user) {
        return new AuthResponse(
                accessTokenService.issue(user),
                "Bearer",
                properties.accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
