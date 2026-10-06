package com.splitledger.budget;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.splitledger.budget.dto.BudgetAmountRequest;
import com.splitledger.budget.dto.BudgetRequest;
import com.splitledger.budget.dto.BudgetResponse;
import com.splitledger.budget.dto.BudgetStatus;
import com.splitledger.common.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Budgets")
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    @Operation(summary = "Your budgets, with spending against each for one month")
    public List<BudgetStatus> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Month as YYYY-MM", example = "2026-09") @RequestParam YearMonth month) {
        return budgetService.statuses(CurrentUser.id(jwt), month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a monthly budget for a category, or an overall one (no categoryId)")
    public BudgetResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BudgetRequest request) {
        return budgetService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change a budget's monthly limit")
    public BudgetResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody BudgetAmountRequest request) {
        return budgetService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        budgetService.delete(CurrentUser.id(jwt), id);
    }
}
