package com.splitledger.admin;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.auth.RefreshTokenService;
import com.splitledger.common.ApiException;
import com.splitledger.user.User;
import com.splitledger.user.UserResponse;
import com.splitledger.user.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Disabling signs the user out everywhere: their refresh tokens are revoked, and
     * {@code EnabledAccountValidator} rejects their current access token on the next request.
     * Admins can't disable themselves, so there's always at least one admin who can undo it.
     */
    @Transactional
    public UserResponse setEnabled(UUID adminId, UUID userId, boolean enabled) {
        if (!enabled && adminId.equals(userId)) {
            throw ApiException.badRequest("You can't disable your own account");
        }
        User user = userService.load(userId);
        if (user.isEnabled() != enabled) {
            user.setEnabled(enabled);
            if (!enabled) {
                refreshTokenService.revokeAll(userId);
            }
            log.info("Admin {} {} user {}", adminId, enabled ? "enabled" : "disabled", userId);
        }
        return UserResponse.from(user);
    }
}
