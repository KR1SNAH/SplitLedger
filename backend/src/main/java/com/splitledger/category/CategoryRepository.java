package com.splitledger.category;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Built-in categories plus the user's own. */
    @Query("""
            select c from Category c left join c.owner o
            where o is null or o.id = :userId
            order by lower(c.name)
            """)
    List<Category> findVisibleTo(UUID userId);
}
