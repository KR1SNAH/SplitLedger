package com.splitledger.budget.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.splitledger.budget.Budget;
import com.splitledger.category.CategoryResponse;

/** A budget without spending figures, returned after it's created or changed. */
public record BudgetResponse(UUID id, CategoryResponse category, BigDecimal amount) {

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.isOverall() ? null : CategoryResponse.from(budget.getCategory()),
                budget.getAmount());
    }
}
