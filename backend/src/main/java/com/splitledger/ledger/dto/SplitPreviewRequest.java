package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.util.List;

import com.splitledger.split.SplitType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The money part of a {@link GroupExpenseRequest}, for showing the shares before saving. */
public record SplitPreviewRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull SplitType splitType,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull ParticipantRequest> participants) {
}
