package com.splitledger.group.invite;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;
import com.splitledger.common.SecureTokens;
import com.splitledger.group.Group;
import com.splitledger.group.GroupMemberRepository;
import com.splitledger.group.GroupRepository;
import com.splitledger.group.GroupService;
import com.splitledger.group.dto.GroupDetail;
import com.splitledger.group.dto.InviteDtos.CreatedInviteLink;
import com.splitledger.group.dto.InviteDtos.GroupInviteResponse;
import com.splitledger.group.dto.InviteDtos.InviteLinkPreview;
import com.splitledger.group.dto.InviteDtos.MyInviteResponse;
import com.splitledger.user.UserRepository;
import com.splitledger.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Two ways into a group:
 * <ul>
 *   <li><b>Invite link:</b> one shareable link per group. Anyone signed in who has it can join until it
 *   expires or an owner replaces or disables it. Only its hash is stored.</li>
 *   <li><b>Email invite:</b> addressed to one email. It shows up for whoever is signed in with that
 *   email, who can accept or decline.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InviteService {

    static final Duration LINK_TTL = Duration.ofDays(7);

    private final GroupService groupService;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    // --- Invite link ---

    @Transactional
    public CreatedInviteLink createLink(UUID groupId) {
        Group group = groupService.lock(groupId);
        String token = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(LINK_TTL);
        group.setInviteLink(SecureTokens.hash(token), expiresAt);
        return new CreatedInviteLink(token, expiresAt);
    }

    @Transactional
    public void disableLink(UUID groupId) {
        groupService.lock(groupId).clearInviteLink();
    }

    @Transactional(readOnly = true)
    public InviteLinkPreview previewLink(UUID userId, String token) {
        Group group = groupForLink(token);
        return new InviteLinkPreview(
                group.getId(),
                group.getName(),
                memberRepository.countByGroupId(group.getId()),
                memberRepository.existsByGroupIdAndUserId(group.getId(), userId));
    }

    /** Joining twice is harmless: an existing member just gets the group back. */
    @Transactional
    public GroupDetail joinByLink(UUID userId, String token) {
        return groupService.join(groupForLink(token).getId(), userId);
    }

    private Group groupForLink(String token) {
        return groupRepository.findByInviteTokenHash(SecureTokens.hash(token))
                .filter(group -> group.hasActiveInviteLink(Instant.now()))
                .orElseThrow(() -> ApiException.notFound("This invite link is invalid or has expired"));
    }

    // --- Email invites ---

    /**
     * The response is the same whether or not the email belongs to a registered user, so invites
     * can't be used to find out who has an account.
     */
    @Transactional
    public GroupInviteResponse invite(UUID groupId, UUID inviterId, String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Group group = groupService.lock(groupId);
        if (memberRepository.existsByGroupIdAndUserEmail(groupId, email)) {
            throw ApiException.conflict(email + " is already a member of this group");
        }
        if (inviteRepository.existsByGroupIdAndEmail(groupId, email)) {
            throw ApiException.conflict(email + " has already been invited");
        }
        GroupInvite invite = new GroupInvite(group, email, userRepository.getReferenceById(inviterId));
        return GroupInviteResponse.from(inviteRepository.save(invite));
    }

    @Transactional(readOnly = true)
    public List<GroupInviteResponse> listForGroup(UUID groupId) {
        return inviteRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(GroupInviteResponse::from)
                .toList();
    }

    @Transactional
    public void cancel(UUID groupId, UUID inviteId) {
        inviteRepository.delete(inviteRepository.findByIdAndGroupId(inviteId, groupId)
                .orElseThrow(() -> ApiException.notFound("Invite not found")));
    }

    @Transactional(readOnly = true)
    public List<MyInviteResponse> listMine(UUID userId) {
        return inviteRepository.findByEmailOrderByCreatedAtDesc(userService.load(userId).getEmail()).stream()
                .map(MyInviteResponse::from)
                .toList();
    }

    @Transactional
    public GroupDetail accept(UUID userId, UUID inviteId) {
        // join() deletes the invite.
        return groupService.join(loadMine(userId, inviteId).getGroup().getId(), userId);
    }

    @Transactional
    public void decline(UUID userId, UUID inviteId) {
        inviteRepository.delete(loadMine(userId, inviteId));
    }

    /** Invites addressed to someone else are reported as not found. */
    private GroupInvite loadMine(UUID userId, UUID inviteId) {
        return inviteRepository.findByIdAndEmail(inviteId, userService.load(userId).getEmail())
                .orElseThrow(() -> ApiException.notFound("Invite not found"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
