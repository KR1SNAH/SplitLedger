package com.splitledger.group.invite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, UUID> {

    @EntityGraph(attributePaths = "invitedBy")
    List<GroupInvite> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

    @EntityGraph(attributePaths = {"group", "invitedBy"})
    List<GroupInvite> findByEmailOrderByCreatedAtDesc(String email);

    Optional<GroupInvite> findByIdAndGroupId(UUID id, UUID groupId);

    Optional<GroupInvite> findByIdAndEmail(UUID id, String email);

    boolean existsByGroupIdAndEmail(UUID groupId, String email);

    @Modifying
    @Query("delete from GroupInvite i where i.group.id = :groupId and i.email = :email")
    void deleteByGroupIdAndEmail(UUID groupId, String email);
}
