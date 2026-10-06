package com.splitledger.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Used for create and update. On update, {@code version} must match the stored version, so a
 * client editing stale data gets a 409 instead of overwriting someone else's change.
 */
public record ExpenseRequest(
        @NotBlank @Size(max = 120) String description,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull LocalDate expenseDate,
        UUID categoryId,
        @Size(max = 500) String notes,
        Long version) {
}
