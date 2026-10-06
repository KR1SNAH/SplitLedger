package com.splitledger.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;
import com.splitledger.common.PageResponse;
import com.splitledger.group.Group;
import com.splitledger.group.GroupMember;
import com.splitledger.group.GroupMemberRepository;
import com.splitledger.group.GroupRole;
import com.splitledger.group.GroupService;
import com.splitledger.ledger.dto.GroupExpenseRequest;
import com.splitledger.ledger.dto.GroupExpenseResponse;
import com.splitledger.ledger.dto.ParticipantRequest;
import com.splitledger.ledger.dto.ShareResponse;
import com.splitledger.ledger.dto.SplitPreviewRequest;
import com.splitledger.split.SplitCalculator;
import com.splitledger.split.SplitType;
import com.splitledger.user.User;

import lombok.RequiredArgsConstructor;

/**
 * Group expenses. Any member can add one; only its creator or a group owner can edit or delete it.
 * <p>
 * Every write takes the group row lock first (the same lock membership changes use), so the check
 * "everyone in the split is a member" can't race with someone leaving the group.
 */
@Service
@RequiredArgsConstructor
public class GroupExpenseService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"));

    private final GroupExpenseRepository expenseRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupService groupService;
    private final BalanceService balanceService;

    private record ComputedShare(User user, BigDecimal input, BigDecimal owed) {
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupExpenseResponse> list(UUID groupId, UUID viewerId, int page, int size) {
        boolean viewerIsOwner = isOwner(groupId, viewerId);
        return PageResponse.from(
                expenseRepository.findByGroupId(groupId, PageRequest.of(page, size, NEWEST_FIRST)),
                expense -> GroupExpenseResponse.from(expense, canModify(expense, viewerId, viewerIsOwner)));
    }

    @Transactional(readOnly = true)
    public GroupExpenseResponse get(UUID groupId, UUID expenseId, UUID viewerId) {
        GroupExpense expense = load(groupId, expenseId);
        return GroupExpenseResponse.from(expense, canModify(expense, viewerId, isOwner(groupId, viewerId)));
    }

    /** Works out the shares exactly as saving would, without saving anything. */
    @Transactional(readOnly = true)
    public List<ShareResponse> preview(UUID groupId, SplitPreviewRequest request) {
        return computeShares(request.amount(), request.splitType(), request.participants(), members(groupId)).stream()
                .map(s -> new ShareResponse(s.user().getId(), s.user().getDisplayName(), s.input(), s.owed()))
                .toList();
    }

    @Transactional
    public GroupExpenseResponse create(UUID groupId, UUID actorId, GroupExpenseRequest request) {
        Group group = groupService.lock(groupId);
        Map<UUID, User> members = members(groupId);
        User creator = requireMember(members, actorId);

        GroupExpense expense = new GroupExpense(group, creator);
        apply(expense, request, members);
        return GroupExpenseResponse.from(expenseRepository.saveAndFlush(expense), true);
    }

    @Transactional
    public GroupExpenseResponse update(UUID groupId, UUID expenseId, UUID actorId, GroupExpenseRequest request) {
        if (request.version() == null) {
            throw ApiException.badRequest("version is required when updating an expense");
        }
        groupService.lock(groupId);
        GroupExpense expense = load(groupId, expenseId);
        requireCanModify(expense, actorId);
        if (!expense.getVersion().equals(request.version())) {
            throw ApiException.conflict("This expense was changed elsewhere. Reload it and try again.");
        }

        // Someone who has left the group can stay on an expense they were already part of, so old
        // expenses stay editable. The check below makes sure their balance isn't changed.
        Map<UUID, User> allowed = new HashMap<>(members(groupId));
        allowed.putIfAbsent(expense.getPaidBy().getId(), expense.getPaidBy());
        expense.getShares().forEach(s -> allowed.putIfAbsent(s.getUser().getId(), s.getUser()));

        apply(expense, request, allowed);
        GroupExpense saved = expenseRepository.saveAndFlush(expense);
        balanceService.requireFormerMembersSettled(groupId);
        return GroupExpenseResponse.from(saved, true);
    }

    @Transactional
    public void delete(UUID groupId, UUID expenseId, UUID actorId) {
        groupService.lock(groupId);
        GroupExpense expense = load(groupId, expenseId);
        requireCanModify(expense, actorId);
        expenseRepository.delete(expense);
        expenseRepository.flush();
        balanceService.requireFormerMembersSettled(groupId);
    }

    private void apply(GroupExpense expense, GroupExpenseRequest request, Map<UUID, User> allowed) {
        User payer = allowed.get(request.paidBy());
        if (payer == null) {
            throw ApiException.badRequest("The person who paid must be a member of the group");
        }
        List<ComputedShare> shares = computeShares(request.amount(), request.splitType(), request.participants(), allowed);

        expense.setDescription(request.description().trim());
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setSplitType(request.splitType());
        expense.setPaidBy(payer);
        expense.setUpdatedAt(Instant.now());
        replaceShares(expense, shares);
    }

    /** Validates the participants and runs the split. Split errors become 400s in the exception handler. */
    private List<ComputedShare> computeShares(
            BigDecimal amount, SplitType type, List<ParticipantRequest> participants, Map<UUID, User> allowed) {
        Set<UUID> seen = new HashSet<>();
        List<User> users = new ArrayList<>();
        for (ParticipantRequest participant : participants) {
            User user = allowed.get(participant.userId());
            if (user == null) {
                throw ApiException.badRequest("Everyone in the split must be a member of the group");
            }
            if (!seen.add(user.getId())) {
                throw ApiException.badRequest(user.getDisplayName() + " is in the split more than once");
            }
            users.add(user);
        }

        List<BigDecimal> inputs = type == SplitType.EQUAL
                ? Collections.nCopies(participants.size(), null)
                : participants.stream().map(ParticipantRequest::value).toList();
        List<BigDecimal> owed = SplitCalculator.split(type, amount, inputs);

        List<ComputedShare> shares = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            shares.add(new ComputedShare(users.get(i), inputs.get(i), owed.get(i)));
        }
        return shares;
    }

    /**
     * Updates shares in place, matched by person, instead of deleting and re-inserting them all.
     * Hibernate flushes inserts before deletes, so re-adding someone as a new row would break the
     * unique (expense, user) constraint.
     */
    private static void replaceShares(GroupExpense expense, List<ComputedShare> computed) {
        Map<UUID, GroupExpenseShare> existing = expense.getShares().stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));
        List<GroupExpenseShare> updated = new ArrayList<>();
        for (int i = 0; i < computed.size(); i++) {
            ComputedShare c = computed.get(i);
            GroupExpenseShare share = existing.get(c.user().getId());
            if (share == null) {
                share = new GroupExpenseShare(expense, c.user());
            }
            share.setSortOrder(i);
            share.setInput(c.input());
            share.setOwed(c.owed());
            updated.add(share);
        }
        // Shares no longer in the list are deleted (orphan removal).
        expense.getShares().clear();
        expense.getShares().addAll(updated);
    }

    private GroupExpense load(UUID groupId, UUID expenseId) {
        return expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> ApiException.notFound("Expense not found"));
    }

    private Map<UUID, User> members(UUID groupId) {
        return memberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUser)
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** Covers the gap between the permission check and taking the lock, in case the actor was removed in between. */
    private static User requireMember(Map<UUID, User> members, UUID userId) {
        User user = members.get(userId);
        if (user == null) {
            throw ApiException.notFound("Group not found");
        }
        return user;
    }

    private void requireCanModify(GroupExpense expense, UUID actorId) {
        if (!canModify(expense, actorId, isOwner(expense.getGroup().getId(), actorId))) {
            throw ApiException.forbidden("Only the person who added this expense or a group owner can change it");
        }
    }

    private static boolean canModify(GroupExpense expense, UUID userId, boolean isOwner) {
        return isOwner || (expense.getCreatedBy() != null && expense.getCreatedBy().getId().equals(userId));
    }

    private boolean isOwner(UUID groupId, UUID userId) {
        return memberRepository.findRole(groupId, userId).orElse(null) == GroupRole.OWNER;
    }
}
