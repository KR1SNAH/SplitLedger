package com.splitledger.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.splitledger.group.dto.GroupSummary;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    @Query("select m.role from GroupMember m where m.group.id = :groupId and m.user.id = :userId")
    Optional<GroupRole> findRole(UUID groupId, UUID userId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserEmail(UUID groupId, String email);

    @EntityGraph(attributePaths = "user")
    List<GroupMember> findByGroupId(UUID groupId);

    long countByGroupId(UUID groupId);

    long countByGroupIdAndRole(UUID groupId, GroupRole role);

    /** The user's groups with their role and each group's member count, in one query. */
    @Query("""
            select new com.splitledger.group.dto.GroupSummary(
                g.id, g.name, g.description, g.currency, m.role,
                (select count(x) from GroupMember x where x.group = g),
                g.createdAt)
            from GroupMember m join m.group g
            where m.user.id = :userId
            order by lower(g.name), g.createdAt
            """)
    List<GroupSummary> findSummaries(UUID userId);
}
