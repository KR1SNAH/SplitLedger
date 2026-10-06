package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Where everyone in a group stands, and the fewest payments that would settle it all.
 *
 * @param members           every current member, including those who are settled up
 * @param suggestedPayments from debt simplification; empty when everyone is settled
 */
public record GroupBalances(String currency, List<MemberBalance> members, List<SuggestedPayment> suggestedPayments) {

    /**
     * @param paid     total of the expenses this member paid for
     * @param owed     total of this member's shares of expenses
     * @param sent     settlements this member paid to others
     * @param received settlements others paid to this member
     * @param net      paid - owed + sent - received: positive means the group owes them money
     */
    public record MemberBalance(
            UserRef user, BigDecimal paid, BigDecimal owed, BigDecimal sent, BigDecimal received, BigDecimal net) {
    }

    public record SuggestedPayment(UserRef from, UserRef to, BigDecimal amount) {
    }
}
