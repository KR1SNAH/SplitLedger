package com.splitledger.ledger;

import java.math.BigDecimal;
import java.util.UUID;

import com.splitledger.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One participant's part of a group expense. */
@Entity
@Table(name = "group_expense_shares")
@Getter
@Setter
@NoArgsConstructor
public class GroupExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false, updatable = false)
    private GroupExpense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** The amount, percentage or number of shares entered for this person; null for EQUAL splits. */
    @Column(name = "share_input", precision = 12, scale = 2)
    private BigDecimal input;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal owed;

    public GroupExpenseShare(GroupExpense expense, User user) {
        this.expense = expense;
        this.user = user;
    }
}
