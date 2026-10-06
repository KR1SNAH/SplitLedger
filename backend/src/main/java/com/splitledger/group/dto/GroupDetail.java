package com.splitledger.group.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.splitledger.group.GroupRole;

/**
 * A group as seen by one of its members.
 *
 * @param inviteLink only sent to owners, and only while a link is active
 */
public record GroupDetail(
        UUID id,
        String name,
        String description,
        String currency,
        GroupRole myRole,
        List<MemberResponse> members,
        InviteLinkStatus inviteLink,
        Instant createdAt) {

    public record InviteLinkStatus(Instant expiresAt) {
    }
}
