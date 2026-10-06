package com.splitledger.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.admin.AdminStats.ContentStats;
import com.splitledger.admin.AdminStats.MonthActivity;
import com.splitledger.admin.AdminStats.UserStats;

import lombok.RequiredArgsConstructor;

/**
 * Reporting queries across every user, in plain SQL: they're aggregates over whole tables, not
 * entity loading, so JPA adds nothing here. Months are bucketed in UTC.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final int MONTHS = 6;

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public AdminStats stats() {
        Instant now = Instant.now();
        OffsetDateTime since = OffsetDateTime.ofInstant(now.minus(Duration.ofDays(30)), ZoneOffset.UTC);

        UserStats users = jdbc.sql("""
                        SELECT count(*),
                               count(*) FILTER (WHERE role = 'ADMIN'),
                               count(*) FILTER (WHERE NOT enabled),
                               count(*) FILTER (WHERE created_at >= :since),
                               (SELECT count(DISTINCT user_id) FROM refresh_tokens WHERE created_at >= :since)
                        FROM users
                        """)
                .param("since", since)
                .query((rs, row) -> new UserStats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)))
                .single();

        ContentStats content = jdbc.sql("""
                        SELECT (SELECT count(*) FROM groups),
                               (SELECT count(*) FROM expenses),
                               (SELECT count(*) FROM group_expenses),
                               (SELECT count(*) FROM settlements)
                        """)
                .query((rs, row) -> new ContentStats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
                .single();

        return new AdminStats(users, content, monthly(YearMonth.from(now.atOffset(ZoneOffset.UTC))));
    }

    /** The last {@link #MONTHS} months, oldest first, with empty months as zero. */
    private List<MonthActivity> monthly(YearMonth current) {
        YearMonth first = current.minusMonths(MONTHS - 1L);
        OffsetDateTime start = first.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<YearMonth, Long> newUsers = countByMonth("""
                SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM'), count(*)
                FROM users
                WHERE created_at >= :start
                GROUP BY 1
                """, start);
        Map<YearMonth, Long> newExpenses = countByMonth("""
                SELECT month, count(*) FROM (
                    SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month FROM expenses WHERE created_at >= :start
                    UNION ALL
                    SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM') FROM group_expenses WHERE created_at >= :start
                ) created
                GROUP BY month
                """, start);

        return Stream.iterate(first, month -> month.plusMonths(1))
                .limit(MONTHS)
                .map(month -> new MonthActivity(month, newUsers.getOrDefault(month, 0L), newExpenses.getOrDefault(month, 0L)))
                .toList();
    }

    private Map<YearMonth, Long> countByMonth(String sql, OffsetDateTime start) {
        return jdbc.sql(sql)
                .param("start", start)
                .query((rs, row) -> Map.entry(YearMonth.parse(rs.getString(1)), rs.getLong(2)))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
