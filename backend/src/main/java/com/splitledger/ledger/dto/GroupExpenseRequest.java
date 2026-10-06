package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.splitledger.split.SplitType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or update a group expense. Participants are listed in order; the order decides who gets a
 * leftover cent on a tie. On update, {@code version} must match the stored version.
 */
public record GroupExpenseRequest(
        @NotBlank @Size(max = 120) String description,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull LocalDate expenseDate,
        @NotNull UUID paidBy,
        @NotNull SplitType splitType,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull ParticipantRequest> participants,
        Long version) {
}
