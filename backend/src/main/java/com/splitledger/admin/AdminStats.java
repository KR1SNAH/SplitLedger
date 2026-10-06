package com.splitledger.admin;

import java.time.YearMonth;
import java.util.List;

/**
 * Platform-wide counts for the admin dashboard. There are deliberately no money totals: amounts
 * are in many different currencies and are never converted.
 */
public record AdminStats(UserStats users, ContentStats content, List<MonthActivity> monthly) {

    /** {@code active}: signed in, or reopened the app, in the last 30 days (a session was issued or refreshed). */
    public record UserStats(long total, long admins, long disabled, long newLast30Days, long activeLast30Days) {
    }

    public record ContentStats(long groups, long personalExpenses, long groupExpenses, long settlements) {
    }

    /** Sign-ups and expenses (personal + group) created in one calendar month, in UTC. */
    public record MonthActivity(YearMonth month, long newUsers, long newExpenses) {
    }
}
