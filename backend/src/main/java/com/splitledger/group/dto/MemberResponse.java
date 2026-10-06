package com.splitledger.group.dto;

import java.time.Instant;
import java.util.UUID;

import com.splitledger.group.GroupMember;
import com.splitledger.group.GroupRole;

public record MemberResponse(UUID userId, String displayName, String email, GroupRole role, Instant joinedAt) {

    public static MemberResponse from(GroupMember member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getDisplayName(),
                member.getUser().getEmail(),
                member.getRole(),
                member.getJoinedAt());
    }
}
