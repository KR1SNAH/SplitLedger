package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Record that {@code fromUserId} paid {@code toUserId}. */
public record SettlementRequest(
        @NotNull UUID fromUserId,
        @NotNull UUID toUserId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull LocalDate settledOn,
        @Size(max = 200) String note) {
}
