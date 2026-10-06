package com.splitledger.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupExpenseRepository extends JpaRepository<GroupExpense, UUID> {

    /** Shares aren't fetched here, since paging over a fetched collection happens in memory; they load in batches instead. */
    @EntityGraph(attributePaths = {"paidBy", "createdBy"})
    Page<GroupExpense> findByGroupId(UUID groupId, Pageable pageable);

    @EntityGraph(attributePaths = {"paidBy", "createdBy", "shares", "shares.user"})
    Optional<GroupExpense> findByIdAndGroupId(UUID id, UUID groupId);

    boolean existsByGroupId(UUID groupId);

    @Query("""
            select new com.splitledger.ledger.UserAmount(e.paidBy.id, sum(e.amount))
            from GroupExpense e
            where e.group.id = :groupId
            group by e.paidBy.id
            """)
    List<UserAmount> totalPaidByUser(UUID groupId);

    @Query("""
            select new com.splitledger.ledger.UserAmount(s.user.id, sum(s.owed))
            from GroupExpenseShare s
            where s.expense.group.id = :groupId
            group by s.user.id
            """)
    List<UserAmount> totalOwedByUser(UUID groupId);

    @Query("""
            select new com.splitledger.ledger.GroupAmount(e.group.id, sum(e.amount))
            from GroupExpense e
            where e.paidBy.id = :userId
            group by e.group.id
            """)
    List<GroupAmount> totalPaidByGroup(UUID userId);

    @Query("""
            select new com.splitledger.ledger.GroupAmount(s.expense.group.id, sum(s.owed))
            from GroupExpenseShare s
            where s.user.id = :userId
            group by s.expense.group.id
            """)
    List<GroupAmount> totalOwedByGroup(UUID userId);
}
