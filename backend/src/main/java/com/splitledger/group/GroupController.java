package com.splitledger.group;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.splitledger.common.CurrentUser;
import com.splitledger.group.dto.ChangeRoleRequest;
import com.splitledger.group.dto.GroupDetail;
import com.splitledger.group.dto.GroupRequest;
import com.splitledger.group.dto.GroupSummary;
import com.splitledger.group.dto.MemberResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Per-group access is checked by {@link GroupPermissions} before each method runs: any member can
 * read, only owners can change things, and non-members get a 404.
 */
@Tag(name = "Groups")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    @Operation(summary = "Groups I belong to")
    public List<GroupSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return groupService.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group (you become its owner)")
    public GroupDetail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GroupRequest request) {
        return groupService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("@groupPermissions.isMember(#groupId, authentication)")
    public GroupDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        return groupService.get(groupId, CurrentUser.id(jwt));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    public GroupDetail update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @Valid @RequestBody GroupRequest request) {
        return groupService.update(groupId, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    public void delete(@PathVariable UUID groupId) {
        groupService.delete(groupId);
    }

    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupPermissions.isMember(#groupId, authentication)")
    @Operation(summary = "Leave a group (the last owner can't)")
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId) {
        groupService.leave(groupId, CurrentUser.id(jwt));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    @Operation(summary = "Remove a member")
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @PathVariable UUID userId) {
        groupService.removeMember(groupId, CurrentUser.id(jwt), userId);
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    @Operation(summary = "Promote a member to owner, or demote an owner")
    public MemberResponse changeRole(
            @PathVariable UUID groupId, @PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
        return groupService.changeRole(groupId, userId, request.role());
    }
}
