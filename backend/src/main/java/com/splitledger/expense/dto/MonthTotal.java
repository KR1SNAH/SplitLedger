package com.splitledger.expense.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Personal spending in one calendar month. {@code month} is serialized as "2026-09". */
public record MonthTotal(YearMonth month, BigDecimal total, long count) {
}
