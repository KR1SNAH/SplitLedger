package com.splitledger.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.splitledger.ledger.GroupExpense;
import com.splitledger.split.SplitType;

/**
 * A group expense as seen by one member.
 *
 * @param canEdit whether the viewer may edit or delete it (its creator or a group owner)
 */
public record GroupExpenseResponse(
        UUID id,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        SplitType splitType,
        UserRef paidBy,
        UserRef createdBy,
        List<ShareResponse> shares,
        boolean canEdit,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static GroupExpenseResponse from(GroupExpense expense, boolean canEdit) {
        return new GroupExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getExpenseDate(),
                expense.getSplitType(),
                UserRef.from(expense.getPaidBy()),
                UserRef.from(expense.getCreatedBy()),
                expense.getShares().stream().map(ShareResponse::from).toList(),
                canEdit,
                expense.getVersion(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
