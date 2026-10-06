package com.splitledger.auth.dto;

import com.splitledger.user.UserResponse;

/** The refresh token is not part of the body; it is set as an HttpOnly cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
