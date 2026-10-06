package com.splitledger.expense;

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

import com.splitledger.common.CurrentUser;
import com.splitledger.common.PageResponse;
import com.splitledger.expense.dto.ExpenseRequest;
import com.splitledger.expense.dto.ExpenseResponse;
import com.splitledger.expense.dto.ExpenseSummary;
import com.splitledger.expense.dto.MonthTotal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Tag(name = "Expenses")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public PageResponse<ExpenseResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Valid ExpenseFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "date|amount|description|created, optionally followed by ,asc or ,desc")
            @RequestParam(defaultValue = "date,desc") String sort) {
        return expenseService.list(CurrentUser.id(jwt), filter, page, size, sort);
    }

    @GetMapping("/summary")
    public ExpenseSummary summary(@AuthenticationPrincipal Jwt jwt, @Valid ExpenseFilter filter) {
        return expenseService.summarize(CurrentUser.id(jwt), filter);
    }

    @GetMapping("/monthly")
    @Operation(summary = "Spending per calendar month, oldest first, for a trend chart")
    public List<MonthTotal> monthly(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Last month to include, as YYYY-MM (default: the current month)")
            @RequestParam(required = false) YearMonth to,
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return expenseService.monthlyTotals(CurrentUser.id(jwt), to == null ? YearMonth.now() : to, months);
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return expenseService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        expenseService.delete(CurrentUser.id(jwt), id);
    }
}
