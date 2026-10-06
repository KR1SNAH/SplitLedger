package com.splitledger.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class MyBalancesIntegrationTest extends LedgerTestSupport {

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;

    @BeforeEach
    void setUp() throws Exception {
        alice = newUser();
        bob = newUser();
        carol = newUser();
    }

    private String createGroup(TestUser owner, String name, String currency) throws Exception {
        return read(mockMvc.perform(withJson(post("/api/groups"), owner.token(), """
                        {"name": "%s", "currency": "%s"}
                        """.formatted(name, currency)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private ResultActions myBalances(TestUser user) throws Exception {
        return mockMvc.perform(as(user, get("/api/balances"))).andExpect(status().isOk());
    }

    @Test
    void netPerGroupAndTotalsPerCurrency() throws Exception {
        // Groups are listed by name: "A trip" (EUR), "B flat" (USD), "C club" (USD).
        String trip = createGroup(alice, "A trip", "EUR");
        String flat = createGroup(alice, "B flat", "USD");
        String club = createGroup(bob, "C club", "USD");
        addMember(alice, trip, bob);
        addMember(alice, flat, bob);
        addMember(alice, flat, carol);
        addMember(bob, club, alice);

        addEqualExpense(trip, alice, "90.00", alice, bob);          // alice +45 EUR
        addEqualExpense(flat, bob, "90.00", alice, bob, carol);     // alice -30 USD
        settle(alice, flat, alice, bob, "10.00");                   // alice -20 USD
        addEqualExpense(club, alice, "30.00", alice, bob);          // alice +15 USD

        myBalances(alice)
                .andExpect(jsonPath("$.groups.length()").value(3))
                .andExpect(jsonPath("$.groups[0].groupName").value("A trip"))
                .andExpect(jsonPath("$.groups[0].currency").value("EUR"))
                .andExpect(jsonPath("$.groups[0].net").value(45.0))
                .andExpect(jsonPath("$.groups[1].groupName").value("B flat"))
                .andExpect(jsonPath("$.groups[1].net").value(-20.0))
                .andExpect(jsonPath("$.groups[2].groupName").value("C club"))
                .andExpect(jsonPath("$.groups[2].net").value(15.0))
                // Amounts are never converted, so there's one total per currency.
                .andExpect(jsonPath("$.totals.length()").value(2))
                .andExpect(jsonPath("$.totals[0].currency").value("EUR"))
                .andExpect(jsonPath("$.totals[0].owedToYou").value(45.0))
                .andExpect(jsonPath("$.totals[0].youOwe").value(0.0))
                .andExpect(jsonPath("$.totals[0].net").value(45.0))
                .andExpect(jsonPath("$.totals[1].currency").value("USD"))
                .andExpect(jsonPath("$.totals[1].owedToYou").value(15.0))
                .andExpect(jsonPath("$.totals[1].youOwe").value(20.0))
                .andExpect(jsonPath("$.totals[1].net").value(-5.0));

        // Carol is only in the flat.
        myBalances(carol)
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].net").value(-30.0))
                .andExpect(jsonPath("$.totals[0].youOwe").value(30.0));
    }

    @Test
    void groupsWithoutActivityShowAsSettled() throws Exception {
        createGroup(alice, "Quiet", "USD");

        myBalances(alice)
                .andExpect(jsonPath("$.groups[0].net").value(0.0))
                .andExpect(jsonPath("$.totals[0].owedToYou").value(0.0))
                .andExpect(jsonPath("$.totals[0].youOwe").value(0.0));
    }

    @Test
    void groupsYouLeftAreNotIncluded() throws Exception {
        String groupId = createGroup(alice, "Short stay", "USD");
        addMember(alice, groupId, bob);
        addEqualExpense(groupId, alice, "20.00", alice, bob);
        settle(bob, groupId, bob, alice, "10.00");
        mockMvc.perform(as(bob, post("/api/groups/" + groupId + "/leave"))).andExpect(status().isNoContent());

        myBalances(bob)
                .andExpect(jsonPath("$.groups.length()").value(0))
                .andExpect(jsonPath("$.totals.length()").value(0));
        myBalances(alice).andExpect(jsonPath("$.groups[0].net").value(0.0));
    }
}
