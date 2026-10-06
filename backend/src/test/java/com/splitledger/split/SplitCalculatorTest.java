package com.splitledger.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SplitCalculatorTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static List<BigDecimal> ds(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private static List<BigDecimal> split(SplitType type, String total, List<BigDecimal> values) {
        return SplitCalculator.split(type, d(total), values);
    }

    private static List<BigDecimal> equal(String total, int people) {
        return split(SplitType.EQUAL, total, Collections.nCopies(people, null));
    }

    @Nested
    class Equal {

        @Test
        void splitsEvenAmountsEvenly() {
            assertThat(equal("90", 3)).containsExactly(d("30.00"), d("30.00"), d("30.00"));
        }

        @Test
        void extraCentsGoToTheFirstParticipants() {
            assertThat(equal("100.00", 3)).containsExactly(d("33.34"), d("33.33"), d("33.33"));
            assertThat(equal("0.05", 3)).containsExactly(d("0.02"), d("0.02"), d("0.01"));
            assertThat(equal("10.00", 6)).containsExactly(d("1.67"), d("1.67"), d("1.67"), d("1.67"), d("1.66"), d("1.66"));
        }

        @Test
        void canLeaveSomeoneOwingNothingWhenThereAreFewerCentsThanPeople() {
            assertThat(equal("0.01", 3)).containsExactly(d("0.01"), d("0.00"), d("0.00"));
        }

        @Test
        void oneParticipantOwesEverything() {
            assertThat(equal("12.34", 1)).containsExactly(d("12.34"));
        }

        @Test
        void ignoresValues() {
            assertThat(split(SplitType.EQUAL, "10", ds("1", "99"))).containsExactly(d("5.00"), d("5.00"));
        }

        @Test
        void handlesTheLargestAllowedAmount() {
            List<BigDecimal> shares = equal("9999999999.99", 7);
            assertThat(shares.getFirst()).isEqualTo(d("1428571428.57"));
            assertThat(sum(shares)).isEqualTo(d("9999999999.99"));
        }
    }

    @Nested
    class Exact {

        @Test
        void usesTheGivenAmounts() {
            assertThat(split(SplitType.EXACT, "30.50", ds("10", "20.5")))
                    .containsExactly(d("10.00"), d("20.50"));
        }

        @Test
        void amountsMustAddUpToTheTotal() {
            assertThatThrownBy(() -> split(SplitType.EXACT, "100", ds("40", "50")))
                    .isInstanceOf(SplitException.class)
                    .hasMessage("The amounts add up to 90.00, but the total is 100.00");
            assertThatThrownBy(() -> split(SplitType.EXACT, "100", ds("40", "60.01")))
                    .hasMessage("The amounts add up to 100.01, but the total is 100.00");
        }

        @Test
        void everyAmountIsRequiredAndPositive() {
            assertThatThrownBy(() -> split(SplitType.EXACT, "10", Arrays.asList(d("10"), null)))
                    .hasMessage("Enter the amount for everyone in the split");
            assertThatThrownBy(() -> split(SplitType.EXACT, "10", ds("10", "0")))
                    .hasMessageStartingWith("Each amount must be greater than 0");
            assertThatThrownBy(() -> split(SplitType.EXACT, "10", ds("15", "-5")))
                    .hasMessageStartingWith("Each amount must be greater than 0");
        }

        @Test
        void amountsHaveAtMostTwoDecimals() {
            assertThatThrownBy(() -> split(SplitType.EXACT, "10", ds("5.005", "4.995")))
                    .hasMessage("Each amount can have at most 2 decimal places");
            // Trailing zeros are fine.
            assertThat(split(SplitType.EXACT, "10", ds("5.000", "5"))).containsExactly(d("5.00"), d("5.00"));
        }
    }

    @Nested
    class Percent {

        @Test
        void splitsByPercentage() {
            assertThat(split(SplitType.PERCENT, "200", ds("50", "30", "20")))
                    .containsExactly(d("100.00"), d("60.00"), d("40.00"));
        }

        @Test
        void leftoverCentsGoToTheLargestRemainder() {
            // Exact shares 3.333, 3.333, 3.334: the last one lost the most to rounding down.
            assertThat(split(SplitType.PERCENT, "10.00", ds("33.33", "33.33", "33.34")))
                    .containsExactly(d("3.33"), d("3.33"), d("3.34"));
            // Exact shares 0.0125 and 0.0875: 1 cent + 8 cents = 9, the leftover cent goes to the second.
            assertThat(split(SplitType.PERCENT, "0.10", ds("12.5", "87.5")))
                    .containsExactly(d("0.01"), d("0.09"));
        }

        @Test
        void percentagesMustAddUpTo100() {
            assertThatThrownBy(() -> split(SplitType.PERCENT, "10", ds("33.33", "33.33", "33.33")))
                    .isInstanceOf(SplitException.class)
                    .hasMessage("The percentages add up to 99.99, not 100");
            assertThatThrownBy(() -> split(SplitType.PERCENT, "10", ds("60", "60")))
                    .hasMessage("The percentages add up to 120, not 100");
        }

        @Test
        void percentagesMustBePositive() {
            assertThatThrownBy(() -> split(SplitType.PERCENT, "10", ds("100", "0")))
                    .hasMessageStartingWith("Each percentage must be greater than 0");
            assertThatThrownBy(() -> split(SplitType.PERCENT, "10", ds("110", "-10")))
                    .hasMessageStartingWith("Each percentage must be greater than 0");
        }
    }

    @Nested
    class Shares {

        @Test
        void splitsInProportion() {
            assertThat(split(SplitType.SHARES, "90", ds("1", "2"))).containsExactly(d("30.00"), d("60.00"));
            assertThat(split(SplitType.SHARES, "10", ds("1.5", "0.5"))).containsExactly(d("7.50"), d("2.50"));
        }

        @Test
        void leftoverCentsGoToTheLargestRemainder() {
            // Exact shares 3.33.. and 6.66.. cents: the second lost more to rounding, so it gets the cent.
            assertThat(split(SplitType.SHARES, "0.10", ds("1", "2"))).containsExactly(d("0.03"), d("0.07"));
        }

        @Test
        void sharesAreRequiredAndPositive() {
            assertThatThrownBy(() -> split(SplitType.SHARES, "10", Arrays.asList(d("1"), null)))
                    .hasMessage("Enter the number of shares for everyone in the split");
            assertThatThrownBy(() -> split(SplitType.SHARES, "10", ds("1", "0")))
                    .hasMessageStartingWith("Each number of shares must be greater than 0");
        }
    }

    @Test
    void needsAtLeastOneParticipant() {
        assertThatThrownBy(() -> split(SplitType.EQUAL, "10", List.of()))
                .isInstanceOf(SplitException.class)
                .hasMessage("Choose at least one person to split with");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "10.001"})
    void rejectsInvalidTotals(String total) {
        assertThatThrownBy(() -> equal(total, 2))
                .isInstanceOf(SplitException.class)
                .hasMessage("The total must be a positive amount with at most 2 decimal places");
    }

    /**
     * Randomized check of the guarantees in the class docs, over thousands of generated splits:
     * the shares add up to the total, each is within a cent of its exact value, and the result is
     * deterministic.
     */
    @ParameterizedTest
    @EnumSource(value = SplitType.class, names = {"EQUAL", "PERCENT", "SHARES"})
    void sharesAlwaysAddUpAndAreWithinACentOfExact(SplitType type) {
        Random random = new Random(42);
        for (int run = 0; run < 2_000; run++) {
            BigDecimal total = BigDecimal.valueOf(1 + random.nextLong(10_000_000), 2);
            int people = 1 + random.nextInt(12);
            List<BigDecimal> weights = switch (type) {
                case EQUAL -> Collections.nCopies(people, BigDecimal.ONE);
                case PERCENT -> randomPercentages(random, people);
                case SHARES -> randomShares(random, people);
                case EXACT -> throw new IllegalStateException();
            };

            List<BigDecimal> shares = SplitCalculator.split(type, total, weights);

            assertThat(shares).hasSize(people);
            assertThat(sum(shares)).as("sum for %s split %s", total, weights).isEqualTo(total);
            BigDecimal weightSum = sum(weights);
            for (int i = 0; i < people; i++) {
                BigDecimal exactShare = total.multiply(weights.get(i)).divide(weightSum, MathContext.DECIMAL128);
                assertThat(shares.get(i).subtract(exactShare).abs())
                        .as("share %d of %s split %s", i, total, weights)
                        .isLessThan(d("0.01"));
                assertThat(shares.get(i).scale()).isEqualTo(2);
            }
            assertThat(SplitCalculator.split(type, total, weights)).isEqualTo(shares);
        }
    }

    /** Positive percentages with up to 2 decimals that add up to exactly 100. */
    private static List<BigDecimal> randomPercentages(Random random, int people) {
        // Cut 10,000 hundredths of a percent into `people` positive pieces.
        List<Integer> cuts = new ArrayList<>();
        while (cuts.size() < people - 1) {
            int cut = 1 + random.nextInt(9_999);
            if (!cuts.contains(cut)) {
                cuts.add(cut);
            }
        }
        Collections.sort(cuts);
        cuts.add(10_000);
        List<BigDecimal> percentages = new ArrayList<>();
        int previous = 0;
        for (int cut : cuts) {
            percentages.add(BigDecimal.valueOf(cut - previous, 2));
            previous = cut;
        }
        return percentages;
    }

    private static List<BigDecimal> randomShares(Random random, int people) {
        List<BigDecimal> shares = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            // 1 to 1000 shares, sometimes with one decimal place (e.g. 1.5).
            shares.add(BigDecimal.valueOf(1 + random.nextInt(1_000), random.nextInt(2)));
        }
        return shares;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
