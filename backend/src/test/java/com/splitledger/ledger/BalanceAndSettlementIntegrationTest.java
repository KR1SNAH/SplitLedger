package com.splitledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class BalanceAndSettlementIntegrationTest extends LedgerTestSupport {

    private TestUser alice; // owner
    private TestUser bob;
    private TestUser carol;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        // Names sort alphabetically, so balances come back in the order alice, bob, carol.
        alice = newUser();
        bob = newUser();
        carol = newUser();
        rename(alice, "Alice");
        rename(bob, "Bob");
        rename(carol, "Carol");
        groupId = createGroup(alice, "Flat 4B");
        addMember(alice, groupId, bob);
        addMember(alice, groupId, carol);
    }

    private void rename(TestUser user, String name) throws Exception {
        mockMvc.perform(withJson(put("/api/users/me"), user.token(), """
                        {"displayName": "%s", "currency": "USD"}
                        """.formatted(name)))
                .andExpect(status().isOk());
    }

    private ResultActions balances(TestUser viewer) throws Exception {
        return mockMvc.perform(as(viewer, get("/api/groups/" + groupId + "/balances"))).andExpect(status().isOk());
    }

    private String settlementPath(String settlementId) {
        return "/api/groups/" + groupId + "/settlements/" + settlementId;
    }

    @Test
    void everyoneStartsSettled() throws Exception {
        balances(bob)
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].user.displayName").value("Alice"))
                .andExpect(jsonPath("$.members[0].net").value(0.0))
                .andExpect(jsonPath("$.suggestedPayments.length()").value(0));
    }

    @Test
    void balancesAddUpPaidOwedAndSettled() throws Exception {
        addEqualExpense(groupId, alice, "90", alice, bob, carol);  // alice +60, bob -30, carol -30
        addEqualExpense(groupId, bob, "30", carol);                 // bob +30 -> 0, carol -30 -> -60

        balances(carol)
                .andExpect(jsonPath("$.members[0].user.userId").value(alice.id()))
                .andExpect(jsonPath("$.members[0].paid").value(90.0))
                .andExpect(jsonPath("$.members[0].owed").value(30.0))
                .andExpect(jsonPath("$.members[0].net").value(60.0))
                .andExpect(jsonPath("$.members[1].paid").value(30.0))
                .andExpect(jsonPath("$.members[1].owed").value(30.0))
                .andExpect(jsonPath("$.members[1].net").value(0.0))
                .andExpect(jsonPath("$.members[2].owed").value(60.0))
                .andExpect(jsonPath("$.members[2].net").value(-60.0))
                // Bob is square, so Carol pays Alice directly.
                .andExpect(jsonPath("$.suggestedPayments.length()").value(1))
                .andExpect(jsonPath("$.suggestedPayments[0].from.userId").value(carol.id()))
                .andExpect(jsonPath("$.suggestedPayments[0].to.userId").value(alice.id()))
                .andExpect(jsonPath("$.suggestedPayments[0].amount").value(60.0));

        settle(carol, groupId, carol, alice, "45");
        balances(carol)
                .andExpect(jsonPath("$.members[0].received").value(45.0))
                .andExpect(jsonPath("$.members[0].net").value(15.0))
                .andExpect(jsonPath("$.members[2].sent").value(45.0))
                .andExpect(jsonPath("$.members[2].net").value(-15.0))
                .andExpect(jsonPath("$.suggestedPayments[0].amount").value(15.0));

        settle(alice, groupId, carol, alice, "15");
        balances(alice)
                .andExpect(jsonPath("$.members[0].net").value(0.0))
                .andExpect(jsonPath("$.members[2].net").value(0.0))
                .andExpect(jsonPath("$.suggestedPayments.length()").value(0));
    }

    @Test
    void settlementsAreListedNewestFirst() throws Exception {
        settle(bob, groupId, bob, carol, "5");
        mockMvc.perform(withJson(post("/api/groups/" + groupId + "/settlements"), carol.token(), """
                        {"fromUserId": "%s", "toUserId": "%s", "amount": 7.5, "settledOn": "2026-09-22", "note": "  Venmo  "}
                        """.formatted(carol.id(), alice.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("Venmo"));

        mockMvc.perform(as(bob, get("/api/groups/" + groupId + "/settlements")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(7.5))
                .andExpect(jsonPath("$[0].from.displayName").value("Carol"))
                .andExpect(jsonPath("$[0].to.displayName").value("Alice"))
                .andExpect(jsonPath("$[0].canDelete").value(false))
                .andExpect(jsonPath("$[1].canDelete").value(true));
    }

    @Test
    void onlyThePeopleInvolvedOrAnOwnerCanRecordOrDeleteAPayment() throws Exception {
        // Bob can't record a payment between two other people...
        mockMvc.perform(postSettlement(bob, groupId, carol, alice, "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can only record payments you made or received"));
        // ...but either person involved can, and so can an owner.
        settle(carol, groupId, carol, alice, "10");
        settle(alice, groupId, alice, carol, "10");
        String settlementId = settle(alice, groupId, bob, carol, "10");

        // Newest first: bob -> carol, alice -> carol, carol -> alice. Bob is only part of the first.
        List<Boolean> canDelete = read(mockMvc.perform(as(bob, get("/api/groups/" + groupId + "/settlements"))).andReturn(),
                "$[*].canDelete");
        assertThat(canDelete).containsExactly(true, false, false);

        TestUser dave = newUser();
        addMember(alice, groupId, dave);
        mockMvc.perform(as(dave, delete(settlementPath(settlementId)))).andExpect(status().isForbidden());
        mockMvc.perform(as(bob, delete(settlementPath(settlementId)))).andExpect(status().isNoContent());
        mockMvc.perform(as(bob, delete(settlementPath(settlementId)))).andExpect(status().isNotFound());
    }

    @Test
    void validatesPayments() throws Exception {
        mockMvc.perform(postSettlement(bob, groupId, bob, bob, "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A payment needs two different people"));
        TestUser outsider = newUser();
        mockMvc.perform(postSettlement(bob, groupId, bob, outsider, "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Both people must be members of the group"));
        mockMvc.perform(postSettlement(bob, groupId, bob, carol, "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        mockMvc.perform(postSettlement(outsider, groupId, outsider, bob, "10")).andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, get("/api/groups/" + groupId + "/balances"))).andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, get("/api/groups/" + groupId + "/settlements"))).andExpect(status().isNotFound());
    }

    @Test
    void membersMustSettleUpBeforeLeavingOrBeingRemoved() throws Exception {
        addEqualExpense(groupId, alice, "20", alice, bob);  // bob owes alice 10

        mockMvc.perform(as(bob, post("/api/groups/" + groupId + "/leave")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You have an unsettled balance in this group. Settle up before leaving."));
        mockMvc.perform(as(alice, delete("/api/groups/" + groupId + "/members/" + bob.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Bob has an unsettled balance in this group. Settle up before removing them."));

        // Carol has no balance, so she can go.
        mockMvc.perform(as(alice, delete("/api/groups/" + groupId + "/members/" + carol.id()))).andExpect(status().isNoContent());

        settle(bob, groupId, bob, alice, "10");
        mockMvc.perform(as(bob, post("/api/groups/" + groupId + "/leave"))).andExpect(status().isNoContent());
        balances(alice).andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void changesCantReopenABalanceForSomeoneWhoLeft() throws Exception {
        String dinner = expenseJson("Dinner", "20", bob, "EQUAL", people(bob, carol));
        String expenseId = addExpense(bob, groupId, dinner);      // carol owes bob 10
        String settlementId = settle(carol, groupId, carol, bob, "10");
        mockMvc.perform(as(carol, post("/api/groups/" + groupId + "/leave"))).andExpect(status().isNoContent());

        // Undoing either record would leave Carol, who has left, owing or owed money.
        mockMvc.perform(as(alice, delete(settlementPath(settlementId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "This would leave Carol, who is no longer in the group, with an unsettled balance. Add them back to the group first."));
        mockMvc.perform(as(bob, delete("/api/groups/" + groupId + "/expenses/" + expenseId))).andExpect(status().isConflict());
        mockMvc.perform(withJson(put("/api/groups/" + groupId + "/expenses/" + expenseId), bob.token(),
                        withVersion(expenseJson("Dinner", "30", bob, "EQUAL", people(bob, carol)), 0)))
                .andExpect(status().isConflict());

        // Edits that leave her share alone are fine, even though she's still in the split.
        mockMvc.perform(withJson(put("/api/groups/" + groupId + "/expenses/" + expenseId), bob.token(),
                        withVersion(dinner.replace("Dinner", "Pizza"), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Pizza"));
        // New expenses can't include her.
        mockMvc.perform(postExpense(bob, groupId, expenseJson("Drinks", "10", bob, "EQUAL", people(bob, carol))))
                .andExpect(status().isBadRequest());
        balances(bob).andExpect(jsonPath("$.suggestedPayments.length()").value(0));
    }

    @Test
    void currencyIsLockedOnceThereIsActivity() throws Exception {
        String path = "/api/groups/" + groupId;
        mockMvc.perform(withJson(put(path), alice.token(), """
                        {"name": "Flat 4B", "currency": "EUR"}
                        """))
                .andExpect(status().isOk());
        settle(bob, groupId, bob, carol, "5");

        mockMvc.perform(withJson(put(path), alice.token(), """
                        {"name": "Flat 4B", "currency": "GBP"}
                        """))
                .andExpect(status().isConflict());
        // Other edits still work.
        mockMvc.perform(withJson(put(path), alice.token(), """
                        {"name": "Flat 5C", "currency": "EUR"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flat 5C"));
        balances(bob).andExpect(jsonPath("$.currency").value("EUR"));
    }

    /**
     * Bob leaving races Alice adding an expense that includes him. Both take the group lock, so one
     * of them sees the other's result: never both succeed (which would leave Bob gone but owing).
     */
    @Test
    void leavingCantRaceAnExpenseThatIncludesYou() throws Exception {
        for (int round = 0; round < 10; round++) {
            TestUser leaver = newUser();
            addMember(alice, groupId, leaver);
            String expense = expenseJson("Race", "10", alice, "EQUAL", people(alice, leaver));

            CountDownLatch start = new CountDownLatch(1);
            List<Callable<Integer>> requests = List.of(
                    () -> {
                        start.await();
                        return mockMvc.perform(as(leaver, post("/api/groups/" + groupId + "/leave"))).andReturn().getResponse().getStatus();
                    },
                    () -> {
                        start.await();
                        return mockMvc.perform(postExpense(alice, groupId, expense)).andReturn().getResponse().getStatus();
                    });

            List<Integer> statuses = new ArrayList<>();
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                List<Future<Integer>> futures = requests.stream().map(pool::submit).toList();
                start.countDown();
                for (Future<Integer> future : futures) {
                    statuses.add(future.get());
                }
            }

            // Either he left first (and the expense is rejected) or the expense came first (and he can't leave).
            assertThat(statuses).isIn(List.of(204, 400), List.of(409, 201));
        }
        balances(alice).andExpect(status().isOk());
    }
}
