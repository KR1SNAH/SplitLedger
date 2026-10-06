package com.splitledger.expense;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    // Fetch the category with each page to avoid one extra query per row.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Expense> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** One row per calendar month that has expenses: [year, month, total, count]. */
    @Query("""
            select year(e.expenseDate), month(e.expenseDate), sum(e.amount), count(e)
            from Expense e
            where e.owner.id = :ownerId and e.expenseDate between :from and :to
            group by year(e.expenseDate), month(e.expenseDate)
            """)
    List<Object[]> monthlyTotals(UUID ownerId, LocalDate from, LocalDate to);
}
