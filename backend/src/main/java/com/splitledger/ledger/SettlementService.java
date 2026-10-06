package com.splitledger.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;
import com.splitledger.group.Group;
import com.splitledger.group.GroupMember;
import com.splitledger.group.GroupMemberRepository;
import com.splitledger.group.GroupRole;
import com.splitledger.group.GroupService;
import com.splitledger.ledger.dto.SettlementRequest;
import com.splitledger.ledger.dto.SettlementResponse;
import com.splitledger.user.User;

import lombok.RequiredArgsConstructor;

/**
 * Recorded payments between members. Either person involved, or a group owner, can record or
 * delete one; a member can't record a payment between two other people. Paying more than you owe
 * is allowed: the balance simply flips the other way.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupService groupService;
    private final BalanceService balanceService;

    @Transactional(readOnly = true)
    public List<SettlementResponse> list(UUID groupId, UUID viewerId) {
        boolean viewerIsOwner = isOwner(groupId, viewerId);
        return settlementRepository.findByGroupIdOrderBySettledOnDescCreatedAtDesc(groupId).stream()
                .map(s -> SettlementResponse.from(s, canModify(s, viewerId, viewerIsOwner)))
                .toList();
    }

    @Transactional
    public SettlementResponse create(UUID groupId, UUID actorId, SettlementRequest request) {
        Group group = groupService.lock(groupId);
        if (request.fromUserId().equals(request.toUserId())) {
            throw ApiException.badRequest("A payment needs two different people");
        }
        if (!canModify(request.fromUserId(), request.toUserId(), actorId, isOwner(groupId, actorId))) {
            throw ApiException.forbidden("You can only record payments you made or received");
        }
        User actor = loadMember(groupId, actorId).orElseThrow(() -> ApiException.notFound("Group not found"));
        User from = loadMember(groupId, request.fromUserId())
                .orElseThrow(() -> ApiException.badRequest("Both people must be members of the group"));
        User to = loadMember(groupId, request.toUserId())
                .orElseThrow(() -> ApiException.badRequest("Both people must be members of the group"));

        Settlement settlement = new Settlement();
        settlement.setGroup(group);
        settlement.setFromUser(from);
        settlement.setToUser(to);
        settlement.setAmount(request.amount());
        settlement.setSettledOn(request.settledOn());
        settlement.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        settlement.setCreatedBy(actor);
        return SettlementResponse.from(settlementRepository.saveAndFlush(settlement), true);
    }

    @Transactional
    public void delete(UUID groupId, UUID settlementId, UUID actorId) {
        groupService.lock(groupId);
        Settlement settlement = settlementRepository.findByIdAndGroupId(settlementId, groupId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!canModify(settlement, actorId, isOwner(groupId, actorId))) {
            throw ApiException.forbidden("Only the people involved or a group owner can delete this payment");
        }
        settlementRepository.delete(settlement);
        settlementRepository.flush();
        balanceService.requireFormerMembersSettled(groupId);
    }

    private Optional<User> loadMember(UUID groupId, UUID userId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId).map(GroupMember::getUser);
    }

    private static boolean canModify(Settlement settlement, UUID userId, boolean isOwner) {
        return canModify(settlement.getFromUser().getId(), settlement.getToUser().getId(), userId, isOwner);
    }

    private static boolean canModify(UUID fromId, UUID toId, UUID userId, boolean isOwner) {
        return isOwner || fromId.equals(userId) || toId.equals(userId);
    }

    private boolean isOwner(UUID groupId, UUID userId) {
        return memberRepository.findRole(groupId, userId).orElse(null) == GroupRole.OWNER;
    }
}
