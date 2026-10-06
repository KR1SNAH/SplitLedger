package com.splitledger.admin;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.splitledger.common.CurrentUser;
import com.splitledger.common.PageResponse;
import com.splitledger.user.UserRepository;
import com.splitledger.user.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** Access is restricted to ROLE_ADMIN by the /api/admin/** rule in SecurityConfig. */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final AdminUserService adminUserService;

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(userRepository.findAll(pageable), UserResponse::from);
    }

    @PostMapping("/{userId}/disable")
    @Operation(summary = "Disable an account and sign it out everywhere, effective immediately")
    public UserResponse disable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return adminUserService.setEnabled(CurrentUser.id(jwt), userId, false);
    }

    @PostMapping("/{userId}/enable")
    @Operation(summary = "Re-enable a disabled account")
    public UserResponse enable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return adminUserService.setEnabled(CurrentUser.id(jwt), userId, true);
    }
}
