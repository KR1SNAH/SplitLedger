package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * One person in a split. {@code value} is the exact amount, percentage or number of shares,
 * depending on the split type; it's ignored for EQUAL splits.
 */
public record ParticipantRequest(
        @NotNull UUID userId,
        @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal value) {
}
