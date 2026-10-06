package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in user's balance in every group they belong to. Groups can use different
 * currencies and amounts are never converted, so the totals are per currency.
 */
public record MyBalances(List<GroupNet> groups, List<CurrencyTotal> totals) {

    /** {@code net}: positive means the group owes you, negative means you owe the group. */
    public record GroupNet(UUID groupId, String groupName, String currency, BigDecimal net) {
    }

    /** Across all groups in one currency: {@code owedToYou} and {@code youOwe} are both non-negative. */
    public record CurrencyTotal(String currency, BigDecimal owedToYou, BigDecimal youOwe, BigDecimal net) {
    }
}
