package com.splitledger.split;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Works out how much each participant owes for an expense. Plain Java with no framework
 * dependencies, so every rule is unit-tested directly.
 * <p>
 * All arithmetic is done in whole cents with exact integers. When an amount doesn't divide evenly,
 * the leftover cents go out by the <em>largest remainder method</em>: everyone first gets their
 * exact share rounded down, then the participants who lost the most to that rounding get one extra
 * cent each, with ties going to whoever comes first in the list. As a result:
 * <ul>
 *   <li>the shares always add up to exactly the total,</li>
 *   <li>nobody is ever more than one cent away from their exact share,</li>
 *   <li>the same input always gives the same result (100.00 split 3 ways is 33.34, 33.33, 33.33).</li>
 * </ul>
 */
public final class SplitCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SplitCalculator() {
    }

    /**
     * @param total  the expense amount, positive with at most 2 decimal places
     * @param values one entry per participant, in order: ignored for {@link SplitType#EQUAL} (may be
     *               null), otherwise the exact amount, the percentage or the number of shares
     * @return what each participant owes, in the same order as {@code values}, with scale 2
     */
    public static List<BigDecimal> split(SplitType type, BigDecimal total, List<BigDecimal> values) {
        if (total == null || total.signum() <= 0 || !hasAtMostTwoDecimals(total)) {
            throw new SplitException("The total must be a positive amount with at most 2 decimal places");
        }
        if (values.isEmpty()) {
            throw new SplitException("Choose at least one person to split with");
        }
        return switch (type) {
            case EQUAL -> allocate(total, Collections.nCopies(values.size(), BigDecimal.ONE));
            case EXACT -> exact(total, values);
            case PERCENT -> percent(total, values);
            case SHARES -> allocate(total, requirePositive(values, "number of shares"));
        };
    }

    private static List<BigDecimal> exact(BigDecimal total, List<BigDecimal> amounts) {
        requirePositive(amounts, "amount");
        BigDecimal sum = sum(amounts);
        if (sum.compareTo(total) != 0) {
            throw new SplitException("The amounts add up to %s, but the total is %s".formatted(money(sum), money(total)));
        }
        return amounts.stream().map(SplitCalculator::money).toList();
    }

    private static List<BigDecimal> percent(BigDecimal total, List<BigDecimal> percentages) {
        requirePositive(percentages, "percentage");
        BigDecimal sum = sum(percentages);
        if (sum.compareTo(HUNDRED) != 0) {
            throw new SplitException("The percentages add up to %s, not 100".formatted(sum.stripTrailingZeros().toPlainString()));
        }
        return allocate(total, percentages);
    }

    /** Splits {@code total} in proportion to {@code weights} using the largest remainder method. */
    private static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        BigInteger cents = toCents(total);
        // Weights have at most 2 decimals, so scaling by 100 makes them exact integers.
        List<BigInteger> scaled = weights.stream().map(SplitCalculator::toCents).toList();
        BigInteger weightSum = scaled.stream().reduce(BigInteger.ZERO, BigInteger::add);

        int n = weights.size();
        long[] shares = new long[n];
        BigInteger[] remainders = new BigInteger[n];
        long allocated = 0;
        for (int i = 0; i < n; i++) {
            // Exact share = cents * weight / weightSum; keep the whole part and remember what was cut off.
            BigInteger[] qr = cents.multiply(scaled.get(i)).divideAndRemainder(weightSum);
            shares[i] = qr[0].longValueExact();
            remainders[i] = qr[1];
            allocated += shares[i];
        }

        // Fewer than n cents are left over, since each share lost less than one cent.
        long leftover = cents.longValueExact() - allocated;
        List<Integer> byLargestRemainder = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparing((Integer i) -> remainders[i]).reversed().thenComparing(i -> i))
                .toList();
        for (int k = 0; k < leftover; k++) {
            shares[byLargestRemainder.get(k)]++;
        }

        List<BigDecimal> result = new ArrayList<>(n);
        for (long share : shares) {
            result.add(BigDecimal.valueOf(share, 2));
        }
        return result;
    }

    private static List<BigDecimal> requirePositive(List<BigDecimal> values, String what) {
        for (BigDecimal value : values) {
            if (value == null) {
                throw new SplitException("Enter the " + what + " for everyone in the split");
            }
            if (value.signum() <= 0) {
                throw new SplitException("Each " + what + " must be greater than 0. Leave people out of the split instead.");
            }
            if (!hasAtMostTwoDecimals(value)) {
                throw new SplitException("Each " + what + " can have at most 2 decimal places");
            }
        }
        return values;
    }

    private static boolean hasAtMostTwoDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 2;
    }

    private static BigInteger toCents(BigDecimal value) {
        return value.movePointRight(2).toBigIntegerExact();
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2);
    }
}
