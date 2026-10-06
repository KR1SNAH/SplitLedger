package com.splitledger.split;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turns a group's balances into a short list of payments that settles everyone up ("debt
 * simplification"). Plain Java with no framework dependencies.
 * <p>
 * Greedy minimum cash flow: repeatedly let the person who owes the most pay the person who is owed
 * the most, as much as settles one of them. Each payment settles at least one person, so a group of
 * n people never needs more than n - 1 payments, and nobody pays someone they don't owe in net
 * terms. Finding the true minimum number of payments is NP-hard, so greedy is the standard practical
 * choice; it occasionally uses a payment or two more than the minimum.
 * <p>
 * Ties are broken by key, so the same balances always produce the same payments.
 */
public final class DebtSimplifier {

    /** {@code from} should pay {@code to} {@code amount}. */
    public record Transfer<K>(K from, K to, BigDecimal amount) {
    }

    private record Party<K>(K key, long cents) {
    }

    private DebtSimplifier() {
    }

    /**
     * @param balances net balance per person: positive means they are owed money, negative means
     *                 they owe money. Must add up to zero, with at most 2 decimal places each.
     * @return the payments to make, largest first
     */
    public static <K extends Comparable<K>> List<Transfer<K>> simplify(Map<K, BigDecimal> balances) {
        Comparator<Party<K>> largestFirst = Comparator
                .comparingLong((Party<K> p) -> p.cents()).reversed()
                .thenComparing(Party::key);
        PriorityQueue<Party<K>> creditors = new PriorityQueue<>(largestFirst);
        PriorityQueue<Party<K>> debtors = new PriorityQueue<>(largestFirst);

        long total = 0;
        for (Map.Entry<K, BigDecimal> entry : balances.entrySet()) {
            long cents = entry.getValue().movePointRight(2).longValueExact();
            total += cents;
            if (cents > 0) {
                creditors.add(new Party<>(entry.getKey(), cents));
            } else if (cents < 0) {
                // Debtors are kept as positive amounts owed, so both queues order the same way.
                debtors.add(new Party<>(entry.getKey(), -cents));
            }
        }
        if (total != 0) {
            throw new IllegalArgumentException("Balances must add up to zero, but they add up to " + BigDecimal.valueOf(total, 2));
        }

        List<Transfer<K>> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Party<K> creditor = creditors.poll();
            Party<K> debtor = debtors.poll();
            long amount = Math.min(creditor.cents(), debtor.cents());
            transfers.add(new Transfer<>(debtor.key(), creditor.key(), BigDecimal.valueOf(amount, 2)));

            if (creditor.cents() > amount) {
                creditors.add(new Party<>(creditor.key(), creditor.cents() - amount));
            }
            if (debtor.cents() > amount) {
                debtors.add(new Party<>(debtor.key(), debtor.cents() - amount));
            }
        }
        return transfers;
    }
}
