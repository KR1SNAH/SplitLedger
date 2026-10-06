package com.splitledger.budget.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.splitledger.budget.Budget;
import com.splitledger.category.CategoryResponse;

/**
 * A budget and how much has been spent against it in one month.
 *
 * @param category    null for the overall budget
 * @param remaining   negative when over budget
 * @param percentUsed spent / amount as a whole percentage, rounded down (can exceed 100)
 */
public record BudgetStatus(
        UUID id,
        CategoryResponse category,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        int percentUsed,
        boolean overBudget) {

    public static BudgetStatus of(Budget budget, BigDecimal spent) {
        BigDecimal amount = budget.getAmount();
        return new BudgetStatus(
                budget.getId(),
                budget.isOverall() ? null : CategoryResponse.from(budget.getCategory()),
                amount,
                spent,
                amount.subtract(spent),
                spent.multiply(BigDecimal.valueOf(100)).divide(amount, 0, RoundingMode.DOWN).intValueExact(),
                spent.compareTo(amount) > 0);
    }
}
