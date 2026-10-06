package com.splitledger.expense.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExpenseSummary(BigDecimal total, long count, List<CategoryTotal> byCategory) {

    /** {@code categoryId}, {@code name} and {@code color} are null for uncategorized expenses. */
    public record CategoryTotal(UUID categoryId, String name, String color, BigDecimal total, long count) {
    }
}
