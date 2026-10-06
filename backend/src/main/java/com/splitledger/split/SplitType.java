package com.splitledger.split;

/** How a group expense is divided between its participants. */
public enum SplitType {
    /** Everyone pays the same. */
    EQUAL,
    /** Each participant's amount is given; they must add up to the total. */
    EXACT,
    /** Each participant pays a percentage; the percentages must add up to 100. */
    PERCENT,
    /** Each participant pays in proportion to a number of shares (e.g. 2 shares for a couple). */
    SHARES
}
