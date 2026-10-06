package com.splitledger.group.invite;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.splitledger.common.CurrentUser;
import com.splitledger.group.dto.GroupDetail;
import com.splitledger.group.dto.InviteDtos.CreatedInviteLink;
import com.splitledger.group.dto.InviteDtos.GroupInviteResponse;
import com.splitledger.group.dto.InviteDtos.InviteLinkPreview;
import com.splitledger.group.dto.InviteDtos.InviteRequest;
import com.splitledger.group.dto.InviteDtos.InviteTokenRequest;
import com.splitledger.group.dto.InviteDtos.MyInviteResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Invites")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    // --- Managing invites (group owners) ---

    @PostMapping("/groups/{groupId}/invite-link")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    @Operation(summary = "Create a new invite link, replacing any existing one. The token is only returned here.")
    public CreatedInviteLink createLink(@PathVariable UUID groupId) {
        return inviteService.createLink(groupId);
    }

    @DeleteMapping("/groups/{groupId}/invite-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    @Operation(summary = "Disable the invite link")
    public void disableLink(@PathVariable UUID groupId) {
        inviteService.disableLink(groupId);
    }

    @GetMapping("/groups/{groupId}/invites")
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    @Operation(summary = "Pending email invites")
    public List<GroupInviteResponse> listForGroup(@PathVariable UUID groupId) {
        return inviteService.listForGroup(groupId);
    }

    @PostMapping("/groups/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    @Operation(summary = "Invite someone by email")
    public GroupInviteResponse invite(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId, @Valid @RequestBody InviteRequest request) {
        return inviteService.invite(groupId, CurrentUser.id(jwt), request.email());
    }

    @DeleteMapping("/groups/{groupId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupPermissions.isOwner(#groupId, authentication)")
    @Operation(summary = "Cancel a pending invite")
    public void cancel(@PathVariable UUID groupId, @PathVariable UUID inviteId) {
        inviteService.cancel(groupId, inviteId);
    }

    // --- Receiving invites (any signed-in user) ---

    @GetMapping("/invites")
    @Operation(summary = "Invites addressed to my email")
    public List<MyInviteResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return inviteService.listMine(CurrentUser.id(jwt));
    }

    @PostMapping("/invites/{inviteId}/accept")
    public GroupDetail accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID inviteId) {
        return inviteService.accept(CurrentUser.id(jwt), inviteId);
    }

    @PostMapping("/invites/{inviteId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID inviteId) {
        inviteService.decline(CurrentUser.id(jwt), inviteId);
    }

    @PostMapping("/invite-links/preview")
    @Operation(summary = "See which group an invite link is for, before joining")
    public InviteLinkPreview previewLink(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteTokenRequest request) {
        return inviteService.previewLink(CurrentUser.id(jwt), request.token());
    }

    @PostMapping("/invite-links/accept")
    @Operation(summary = "Join a group with an invite link")
    public GroupDetail joinByLink(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteTokenRequest request) {
        return inviteService.joinByLink(CurrentUser.id(jwt), request.token());
    }
}
