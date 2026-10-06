package com.splitledger.user;

import java.util.Currency;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return UserResponse.from(load(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        try {
            Currency.getInstance(request.currency());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown currency: " + request.currency());
        }
        User user = load(userId);
        user.setDisplayName(request.displayName().trim());
        user.setCurrency(request.currency());
        return UserResponse.from(user);
    }

    public User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
