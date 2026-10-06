package com.splitledger.group;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.splitledger.common.ApiException;

import lombok.RequiredArgsConstructor;

/**
 * Per-group authorization, used from {@code @PreAuthorize} on controller methods, for example
 * {@code @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")}.
 * <p>
 * Non-members get a 404 (thrown from here) instead of a 403, so they can't tell whether a group
 * exists. Members who try an owner-only action get a 403.
 */
@Component("groupPermissions")
@RequiredArgsConstructor
public class GroupPermissions {

    private final GroupMemberRepository memberRepository;

    /** Always true when it returns; non-members get a 404. */
    public boolean isMember(UUID groupId, Authentication authentication) {
        roleOf(groupId, authentication);
        return true;
    }

    public boolean isOwner(UUID groupId, Authentication authentication) {
        return roleOf(groupId, authentication) == GroupRole.OWNER;
    }

    private GroupRole roleOf(UUID groupId, Authentication authentication) {
        // The JWT subject (and so the authentication name) is the user's id.
        UUID userId = UUID.fromString(authentication.getName());
        return memberRepository.findRole(groupId, userId)
                .orElseThrow(() -> ApiException.notFound("Group not found"));
    }
}
