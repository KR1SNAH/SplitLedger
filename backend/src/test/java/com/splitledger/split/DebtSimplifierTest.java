package com.splitledger.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.splitledger.split.DebtSimplifier.Transfer;

class DebtSimplifierTest {

    private static Transfer<String> pay(String from, String to, String amount) {
        return new Transfer<>(from, to, new BigDecimal(amount));
    }

    private static Map<String, BigDecimal> balances(String... keysAndAmounts) {
        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        for (int i = 0; i < keysAndAmounts.length; i += 2) {
            balances.put(keysAndAmounts[i], new BigDecimal(keysAndAmounts[i + 1]));
        }
        return balances;
    }

    @Test
    void nothingToDoWhenEveryoneIsSettled() {
        assertThat(DebtSimplifier.simplify(Map.<String, BigDecimal>of())).isEmpty();
        assertThat(DebtSimplifier.simplify(balances("alice", "0", "bob", "0.00"))).isEmpty();
    }

    @Test
    void debtorPaysCreditor() {
        assertThat(DebtSimplifier.simplify(balances("alice", "25.50", "bob", "-25.50")))
                .containsExactly(pay("bob", "alice", "25.50"));
    }

    @Test
    void chainsCollapseIntoOnePayment() {
        // Alice owes Bob 10 and Bob owes Carol 10: Bob's net is zero, so Alice just pays Carol.
        assertThat(DebtSimplifier.simplify(balances("alice", "-10", "bob", "0", "carol", "10")))
                .containsExactly(pay("alice", "carol", "10.00"));
    }

    @Test
    void largestDebtIsSettledFirst() {
        assertThat(DebtSimplifier.simplify(balances("alice", "30", "bob", "-10", "carol", "-20")))
                .containsExactly(pay("carol", "alice", "20.00"), pay("bob", "alice", "10.00"));
    }

    @Test
    void splitsADebtAcrossSeveralCreditors() {
        assertThat(DebtSimplifier.simplify(balances("alice", "60", "bob", "40", "carol", "-100")))
                .containsExactly(pay("carol", "alice", "60.00"), pay("carol", "bob", "40.00"));
    }

    @Test
    void tiesAreBrokenByKeySoTheResultIsStable() {
        Map<String, BigDecimal> balances = balances("dan", "-10", "carol", "-10", "bob", "10", "alice", "10");
        assertThat(DebtSimplifier.simplify(balances))
                .containsExactly(pay("carol", "alice", "10.00"), pay("dan", "bob", "10.00"));
        assertThat(DebtSimplifier.simplify(new HashMap<>(balances)))
                .isEqualTo(DebtSimplifier.simplify(balances));
    }

    @Test
    void balancesMustAddUpToZero() {
        assertThatThrownBy(() -> DebtSimplifier.simplify(balances("alice", "10", "bob", "-9.99")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.01");
    }

    /**
     * Randomized check over many generated groups: the payments settle everyone exactly, nobody
     * both pays and receives, money only flows from debtors to creditors, and there are never more
     * than n - 1 payments.
     */
    @Test
    void alwaysSettlesEveryoneWithAtMostNMinusOnePayments() {
        Random random = new Random(7);
        for (int run = 0; run < 2_000; run++) {
            int people = 2 + random.nextInt(15);
            Map<String, BigDecimal> balances = new HashMap<>();
            long total = 0;
            for (int i = 0; i < people - 1; i++) {
                long cents = random.nextLong(-50_000, 50_001);
                balances.put("p" + i, BigDecimal.valueOf(cents, 2));
                total += cents;
            }
            balances.put("p" + (people - 1), BigDecimal.valueOf(-total, 2));
            long nonZero = balances.values().stream().filter(b -> b.signum() != 0).count();

            List<Transfer<String>> transfers = DebtSimplifier.simplify(balances);

            assertThat(transfers.size()).isLessThanOrEqualTo((int) Math.max(0, nonZero - 1));
            Map<String, BigDecimal> remaining = new HashMap<>(balances);
            for (Transfer<String> t : transfers) {
                assertThat(t.amount()).isPositive();
                assertThat(balances.get(t.from())).as("payer %s owes money", t.from()).isNegative();
                assertThat(balances.get(t.to())).as("recipient %s is owed money", t.to()).isPositive();
                remaining.merge(t.from(), t.amount(), BigDecimal::add);
                remaining.merge(t.to(), t.amount().negate(), BigDecimal::add);
            }
            assertThat(remaining.values()).allSatisfy(b -> assertThat(b.signum()).isZero());
        }
    }
}
