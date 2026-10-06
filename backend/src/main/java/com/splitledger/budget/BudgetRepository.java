package com.splitledger.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @EntityGraph(attributePaths = "category")
    List<Budget> findByOwnerId(UUID ownerId);

    @EntityGraph(attributePaths = "category")
    Optional<Budget> findByIdAndOwnerId(UUID id, UUID ownerId);
}
