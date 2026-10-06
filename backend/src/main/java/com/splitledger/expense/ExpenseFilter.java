package com.splitledger.expense;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** Query-string filters shared by the expense list and summary endpoints. All fields are optional. */
public record ExpenseFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        UUID categoryId,
        @Size(max = 100) String q) {

    @AssertTrue(message = "from must not be after to")
    public boolean isDateRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }

    /** Builds the WHERE clause, always scoped to the given user's expenses. */
    Predicate toPredicate(UUID userId, Root<Expense> root, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("owner").get("id"), userId));
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
        }
        if (categoryId != null) {
            predicates.add(cb.equal(root.get("category").get("id"), categoryId));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
            predicates.add(cb.like(cb.lower(root.get("description")), pattern, '\\'));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
