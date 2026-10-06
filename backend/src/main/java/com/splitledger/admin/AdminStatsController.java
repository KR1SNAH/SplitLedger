package com.splitledger.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Access is restricted to ROLE_ADMIN by the /api/admin/** rule in SecurityConfig. */
@Tag(name = "Admin")
@RestController
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/api/admin/stats")
    @Operation(summary = "Platform-wide user and activity counts")
    public AdminStats stats() {
        return adminStatsService.stats();
    }
}
