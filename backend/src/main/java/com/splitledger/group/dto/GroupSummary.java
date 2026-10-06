package com.splitledger.group.dto;

import java.time.Instant;
import java.util.UUID;

import com.splitledger.group.GroupRole;

/** One row of "my groups". Built directly by a JPQL constructor expression. */
public record GroupSummary(
        UUID id,
        String name,
        String description,
        String currency,
        GroupRole myRole,
        long memberCount,
        Instant createdAt) {
}
