package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.splitledger.ledger.Settlement;

/** @param canDelete whether the viewer may delete it (either person involved, or a group owner) */
public record SettlementResponse(
        UUID id,
        UserRef from,
        UserRef to,
        BigDecimal amount,
        LocalDate settledOn,
        String note,
        UserRef createdBy,
        boolean canDelete,
        Instant createdAt) {

    public static SettlementResponse from(Settlement settlement, boolean canDelete) {
        return new SettlementResponse(
                settlement.getId(),
                UserRef.from(settlement.getFromUser()),
                UserRef.from(settlement.getToUser()),
                settlement.getAmount(),
                settlement.getSettledOn(),
                settlement.getNote(),
                UserRef.from(settlement.getCreatedBy()),
                canDelete,
                settlement.getCreatedAt());
    }
}
