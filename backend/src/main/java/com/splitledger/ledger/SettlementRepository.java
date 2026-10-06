package com.splitledger.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    @EntityGraph(attributePaths = {"fromUser", "toUser", "createdBy"})
    List<Settlement> findByGroupIdOrderBySettledOnDescCreatedAtDesc(UUID groupId);

    @EntityGraph(attributePaths = {"fromUser", "toUser"})
    Optional<Settlement> findByIdAndGroupId(UUID id, UUID groupId);

    boolean existsByGroupId(UUID groupId);

    @Query("""
            select new com.splitledger.ledger.UserAmount(s.fromUser.id, sum(s.amount))
            from Settlement s
            where s.group.id = :groupId
            group by s.fromUser.id
            """)
    List<UserAmount> totalSentByUser(UUID groupId);

    @Query("""
            select new com.splitledger.ledger.UserAmount(s.toUser.id, sum(s.amount))
            from Settlement s
            where s.group.id = :groupId
            group by s.toUser.id
            """)
    List<UserAmount> totalReceivedByUser(UUID groupId);

    @Query("""
            select new com.splitledger.ledger.GroupAmount(s.group.id, sum(s.amount))
            from Settlement s
            where s.fromUser.id = :userId
            group by s.group.id
            """)
    List<GroupAmount> totalSentByGroup(UUID userId);

    @Query("""
            select new com.splitledger.ledger.GroupAmount(s.group.id, sum(s.amount))
            from Settlement s
            where s.toUser.id = :userId
            group by s.group.id
            """)
    List<GroupAmount> totalReceivedByGroup(UUID userId);
}
