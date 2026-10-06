package com.splitledger.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;
import com.splitledger.group.Group;
import com.splitledger.group.GroupMember;
import com.splitledger.group.GroupMemberRepository;
import com.splitledger.group.GroupRepository;
import com.splitledger.ledger.dto.GroupBalances;
import com.splitledger.ledger.dto.GroupBalances.MemberBalance;
import com.splitledger.ledger.dto.GroupBalances.SuggestedPayment;
import com.splitledger.ledger.dto.MyBalances;
import com.splitledger.ledger.dto.MyBalances.CurrencyTotal;
import com.splitledger.ledger.dto.MyBalances.GroupNet;
import com.splitledger.ledger.dto.UserRef;
import com.splitledger.split.DebtSimplifier;
import com.splitledger.user.User;
import com.splitledger.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Balances are derived from expenses and settlements every time rather than stored, so they can
 * never drift out of sync with the records they come from. Four grouped SUM queries per group.
 * <p>
 * The ledger keeps one invariant: <b>only current members can have a non-zero balance</b>. Members
 * with money outstanding can't leave or be removed, and edits to old expenses or settlements
 * can't reopen a balance for someone who has already left.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final GroupExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public GroupBalances balances(UUID groupId) {
        Group group = groupRepository.findById(groupId).orElseThrow(() -> ApiException.notFound("Group not found"));
        Ledger ledger = ledger(groupId);

        List<User> members = memberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUser)
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<MemberBalance> balances = new ArrayList<>();
        Map<UUID, UserRef> refs = new HashMap<>();
        Map<UUID, BigDecimal> nets = new HashMap<>();
        for (User user : members) {
            UUID id = user.getId();
            UserRef ref = UserRef.from(user);
            refs.put(id, ref);
            nets.put(id, ledger.net(id));
            balances.add(new MemberBalance(
                    ref, ledger.paid(id), ledger.owed(id), ledger.sent(id), ledger.received(id), ledger.net(id)));
        }

        List<SuggestedPayment> payments = DebtSimplifier.simplify(nets).stream()
                .map(t -> new SuggestedPayment(refs.get(t.from()), refs.get(t.to()), t.amount()))
                .toList();
        return new GroupBalances(group.getCurrency(), balances, payments);
    }

    /**
     * The user's net balance in each of their groups, plus totals per currency. Four grouped
     * queries in all, however many groups they're in. Groups the user has left aren't included:
     * the ledger invariant guarantees their balance there is zero.
     */
    @Transactional(readOnly = true)
    public MyBalances myBalances(UUID userId) {
        Map<UUID, BigDecimal> paid = toGroupMap(expenseRepository.totalPaidByGroup(userId));
        Map<UUID, BigDecimal> owed = toGroupMap(expenseRepository.totalOwedByGroup(userId));
        Map<UUID, BigDecimal> sent = toGroupMap(settlementRepository.totalSentByGroup(userId));
        Map<UUID, BigDecimal> received = toGroupMap(settlementRepository.totalReceivedByGroup(userId));

        List<GroupNet> groups = memberRepository.findSummaries(userId).stream()
                .map(g -> new GroupNet(g.id(), g.name(), g.currency(), Ledger.ZERO
                        .add(paid.getOrDefault(g.id(), Ledger.ZERO))
                        .subtract(owed.getOrDefault(g.id(), Ledger.ZERO))
                        .add(sent.getOrDefault(g.id(), Ledger.ZERO))
                        .subtract(received.getOrDefault(g.id(), Ledger.ZERO))))
                .toList();

        List<CurrencyTotal> totals = groups.stream()
                .collect(Collectors.groupingBy(GroupNet::currency, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    BigDecimal owedToYou = sum(entry.getValue().stream().map(GroupNet::net).filter(n -> n.signum() > 0));
                    BigDecimal youOwe = sum(entry.getValue().stream().map(GroupNet::net).filter(n -> n.signum() < 0)).negate();
                    return new CurrencyTotal(entry.getKey(), owedToYou, youOwe, owedToYou.subtract(youOwe));
                })
                .toList();
        return new MyBalances(groups, totals);
    }

    @Transactional(readOnly = true)
    public BigDecimal netBalance(UUID groupId, UUID userId) {
        return ledger(groupId).net(userId);
    }

    /** Whether the group has any expenses or settlements. */
    @Transactional(readOnly = true)
    public boolean hasActivity(UUID groupId) {
        return expenseRepository.existsByGroupId(groupId) || settlementRepository.existsByGroupId(groupId);
    }

    /**
     * Enforces the invariant after a change to existing expenses or settlements: fails with a 409
     * (rolling the change back) if it left anyone who is no longer a member with money outstanding.
     * Call it while holding the group lock, after flushing the change.
     */
    @Transactional(readOnly = true)
    public void requireFormerMembersSettled(UUID groupId) {
        Ledger ledger = ledger(groupId);
        Set<UUID> memberIds = memberRepository.findByGroupId(groupId).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());
        for (UUID userId : ledger.userIds()) {
            if (!memberIds.contains(userId) && ledger.net(userId).signum() != 0) {
                throw ApiException.conflict(("This would leave %s, who is no longer in the group, with an unsettled "
                        + "balance. Add them back to the group first.").formatted(userService.load(userId).getDisplayName()));
            }
        }
    }

    private Ledger ledger(UUID groupId) {
        return new Ledger(
                toMap(expenseRepository.totalPaidByUser(groupId)),
                toMap(expenseRepository.totalOwedByUser(groupId)),
                toMap(settlementRepository.totalSentByUser(groupId)),
                toMap(settlementRepository.totalReceivedByUser(groupId)));
    }

    private static Map<UUID, BigDecimal> toMap(List<UserAmount> amounts) {
        return amounts.stream().collect(Collectors.toMap(UserAmount::userId, UserAmount::amount));
    }

    private static Map<UUID, BigDecimal> toGroupMap(List<GroupAmount> amounts) {
        return amounts.stream().collect(Collectors.toMap(GroupAmount::groupId, GroupAmount::amount));
    }

    private static BigDecimal sum(Stream<BigDecimal> amounts) {
        return amounts.reduce(Ledger.ZERO, BigDecimal::add);
    }

    /** Per-user totals for one group. Missing entries are zero. */
    private record Ledger(
            Map<UUID, BigDecimal> paid, Map<UUID, BigDecimal> owed,
            Map<UUID, BigDecimal> sent, Map<UUID, BigDecimal> received) {

        private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

        BigDecimal paid(UUID userId) {
            return paid.getOrDefault(userId, ZERO);
        }

        BigDecimal owed(UUID userId) {
            return owed.getOrDefault(userId, ZERO);
        }

        BigDecimal sent(UUID userId) {
            return sent.getOrDefault(userId, ZERO);
        }

        BigDecimal received(UUID userId) {
            return received.getOrDefault(userId, ZERO);
        }

        /** Positive: the group owes them. Negative: they owe the group. */
        BigDecimal net(UUID userId) {
            return paid(userId).subtract(owed(userId)).add(sent(userId)).subtract(received(userId));
        }

        Set<UUID> userIds() {
            Set<UUID> ids = new HashSet<>(paid.keySet());
            ids.addAll(owed.keySet());
            ids.addAll(sent.keySet());
            ids.addAll(received.keySet());
            return ids;
        }
    }
}
