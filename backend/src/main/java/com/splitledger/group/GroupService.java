package com.splitledger.group;

import java.time.Instant;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;
import com.splitledger.group.dto.GroupDetail;
import com.splitledger.group.dto.GroupDetail.InviteLinkStatus;
import com.splitledger.group.dto.GroupRequest;
import com.splitledger.group.dto.GroupSummary;
import com.splitledger.group.dto.MemberResponse;
import com.splitledger.group.invite.GroupInviteRepository;
import com.splitledger.ledger.BalanceService;
import com.splitledger.user.User;
import com.splitledger.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Groups and their membership. Who may call what is decided in the controller with
 * {@link GroupPermissions}; this class enforces the invariants that hold regardless of who asks:
 * every group always has at least one owner, and nobody leaves (or is removed) with money
 * outstanding.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private static final Comparator<GroupMember> OWNERS_FIRST = Comparator
            .comparing((GroupMember m) -> m.getRole() != GroupRole.OWNER)
            .thenComparing(m -> m.getUser().getDisplayName(), String.CASE_INSENSITIVE_ORDER);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupInviteRepository inviteRepository;
    private final UserService userService;
    private final BalanceService balanceService;

    @Transactional(readOnly = true)
    public List<GroupSummary> list(UUID userId) {
        return memberRepository.findSummaries(userId);
    }

    /** The creator becomes the group's first owner. */
    @Transactional
    public GroupDetail create(UUID userId, GroupRequest request) {
        User creator = userService.load(userId);
        Group group = new Group(creator);
        apply(group, request, creator.getCurrency());
        groupRepository.save(group);
        memberRepository.save(new GroupMember(group, creator, GroupRole.OWNER));
        return toDetail(group, userId);
    }

    @Transactional(readOnly = true)
    public GroupDetail get(UUID groupId, UUID userId) {
        return toDetail(load(groupId), userId);
    }

    /** The currency is fixed once there are expenses or settlements, since amounts are never converted. */
    @Transactional
    public GroupDetail update(UUID groupId, UUID userId, GroupRequest request) {
        Group group = lock(groupId);
        if (request.currency() != null && !request.currency().equals(group.getCurrency())
                && balanceService.hasActivity(groupId)) {
            throw ApiException.conflict("The currency can't be changed once the group has expenses or payments");
        }
        apply(group, request, group.getCurrency());
        return toDetail(group, userId);
    }

    /** Members, pending invites, expenses and payments go with it (ON DELETE CASCADE). */
    @Transactional
    public void delete(UUID groupId) {
        groupRepository.delete(load(groupId));
    }

    /** Adds the user as a member (no-op if they already are) and clears any pending email invite for them. */
    @Transactional
    public GroupDetail join(UUID groupId, UUID userId) {
        Group group = lock(groupId);
        User user = userService.load(userId);
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            memberRepository.save(new GroupMember(group, user, GroupRole.MEMBER));
        }
        inviteRepository.deleteByGroupIdAndEmail(groupId, user.getEmail());
        return toDetail(group, userId);
    }

    @Transactional
    public void leave(UUID groupId, UUID userId) {
        lock(groupId);
        GroupMember member = loadMember(groupId, userId);
        if (member.isOwner() && isOnlyOwner(groupId)) {
            throw ApiException.conflict(
                    "You're the only owner. Make another member an owner first, or delete the group.");
        }
        if (balanceService.netBalance(groupId, userId).signum() != 0) {
            throw ApiException.conflict("You have an unsettled balance in this group. Settle up before leaving.");
        }
        memberRepository.delete(member);
    }

    /**
     * Owners can remove anyone except themselves (they leave instead) who is settled up. Because the
     * caller is an owner and stays in the group, this can never remove the last owner.
     */
    @Transactional
    public void removeMember(UUID groupId, UUID actorId, UUID userId) {
        if (actorId.equals(userId)) {
            throw ApiException.badRequest("To remove yourself, leave the group instead");
        }
        lock(groupId);
        GroupMember member = loadMember(groupId, userId);
        if (balanceService.netBalance(groupId, userId).signum() != 0) {
            throw ApiException.conflict(member.getUser().getDisplayName()
                    + " has an unsettled balance in this group. Settle up before removing them.");
        }
        memberRepository.delete(member);
    }

    @Transactional
    public MemberResponse changeRole(UUID groupId, UUID userId, GroupRole role) {
        lock(groupId);
        GroupMember member = loadMember(groupId, userId);
        if (member.isOwner() && role != GroupRole.OWNER && isOnlyOwner(groupId)) {
            throw ApiException.conflict("A group needs at least one owner. Make another member an owner first.");
        }
        member.setRole(role);
        return MemberResponse.from(member);
    }

    Group load(UUID groupId) {
        return groupRepository.findById(groupId).orElseThrow(() -> ApiException.notFound("Group not found"));
    }

    /** Loads the group with a row lock held until the transaction ends. */
    public Group lock(UUID groupId) {
        return groupRepository.findWithLockById(groupId).orElseThrow(() -> ApiException.notFound("Group not found"));
    }

    private GroupMember loadMember(UUID groupId, UUID userId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
    }

    /** Only meaningful while holding the group lock; otherwise the count could change underneath us. */
    private boolean isOnlyOwner(UUID groupId) {
        return memberRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER) <= 1;
    }

    private void apply(Group group, GroupRequest request, String defaultCurrency) {
        String currency = request.currency() == null ? defaultCurrency : request.currency();
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown currency: " + currency);
        }
        group.setName(request.name().trim());
        group.setDescription(request.description() == null || request.description().isBlank()
                ? null
                : request.description().trim());
        group.setCurrency(currency);
    }

    private GroupDetail toDetail(Group group, UUID userId) {
        List<GroupMember> members = memberRepository.findByGroupId(group.getId()).stream()
                .sorted(OWNERS_FIRST)
                .toList();
        GroupRole myRole = members.stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .map(GroupMember::getRole)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Group not found"));

        InviteLinkStatus inviteLink = myRole == GroupRole.OWNER && group.hasActiveInviteLink(Instant.now())
                ? new InviteLinkStatus(group.getInviteExpiresAt())
                : null;

        return new GroupDetail(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getCurrency(),
                myRole,
                members.stream().map(MemberResponse::from).toList(),
                inviteLink,
                group.getCreatedAt());
    }
}
