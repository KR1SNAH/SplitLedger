package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.splitledger.ledger.GroupExpenseShare;

/** @param value what was entered for this person (amount, percentage or shares); null for EQUAL splits */
public record ShareResponse(UUID userId, String displayName, BigDecimal value, BigDecimal owed) {

    public static ShareResponse from(GroupExpenseShare share) {
        return new ShareResponse(share.getUser().getId(), share.getUser().getDisplayName(), share.getInput(), share.getOwed());
    }
}
