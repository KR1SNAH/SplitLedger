package com.splitledger.group;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    /**
     * SELECT ... FOR UPDATE. Membership changes lock the group row first, so rules like "a group
     * always has an owner" can't be broken by two requests racing each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Group> findWithLockById(UUID id);

    Optional<Group> findByInviteTokenHash(String inviteTokenHash);
}
