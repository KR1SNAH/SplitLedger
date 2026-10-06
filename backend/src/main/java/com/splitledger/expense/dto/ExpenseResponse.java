package com.splitledger.expense.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.splitledger.category.CategoryResponse;
import com.splitledger.expense.Expense;

public record ExpenseResponse(
        UUID id,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        CategoryResponse category,
        String notes,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getExpenseDate(),
                expense.getCategory() == null ? null : CategoryResponse.from(expense.getCategory()),
                expense.getNotes(),
                expense.getVersion(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
