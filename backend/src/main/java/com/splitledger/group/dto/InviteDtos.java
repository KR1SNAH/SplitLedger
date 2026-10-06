package com.splitledger.group.dto;

import java.time.Instant;
import java.util.UUID;

import com.splitledger.group.invite.GroupInvite;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes for group invites (by email and by link). */
public final class InviteDtos {

    private InviteDtos() {
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    /** The raw token is sent in the body rather than the URL so it doesn't end up in access logs. */
    public record InviteTokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    /** Returned once, when the link is created. Only its hash is stored, so it can't be shown again. */
    public record CreatedInviteLink(String token, Instant expiresAt) {
    }

    /** What someone opening an invite link sees before joining. */
    public record InviteLinkPreview(UUID groupId, String groupName, long memberCount, boolean alreadyMember) {
    }

    /** A pending invite, as the group's owners see it. */
    public record GroupInviteResponse(UUID id, String email, String invitedBy, Instant createdAt) {

        public static GroupInviteResponse from(GroupInvite invite) {
            return new GroupInviteResponse(invite.getId(), invite.getEmail(), inviterName(invite), invite.getCreatedAt());
        }
    }

    /** A pending invite, as the invited user sees it. */
    public record MyInviteResponse(UUID id, UUID groupId, String groupName, String invitedBy, Instant createdAt) {

        public static MyInviteResponse from(GroupInvite invite) {
            return new MyInviteResponse(
                    invite.getId(), invite.getGroup().getId(), invite.getGroup().getName(), inviterName(invite),
                    invite.getCreatedAt());
        }
    }

    private static String inviterName(GroupInvite invite) {
        return invite.getInvitedBy() == null ? null : invite.getInvitedBy().getDisplayName();
    }
}
