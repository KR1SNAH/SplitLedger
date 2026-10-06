package com.splitledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class GroupExpenseIntegrationTest extends LedgerTestSupport {

    private TestUser alice; // owner
    private TestUser bob;
    private TestUser carol;
    private TestUser outsider;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        alice = newUser();
        bob = newUser();
        carol = newUser();
        outsider = newUser();
        groupId = createGroup(alice, "Ski trip");
        addMember(alice, groupId, bob);
        addMember(alice, groupId, carol);
    }

    private String path() {
        return "/api/groups/" + groupId + "/expenses";
    }

    private MockHttpServletRequestBuilder putExpense(TestUser actor, String expenseId, String json) {
        return withJson(put(path() + "/" + expenseId), actor.token(), json);
    }

    @Test
    void equalSplitGivesLeftoverCentsToTheFirstParticipants() throws Exception {
        mockMvc.perform(postExpense(bob, groupId, expenseJson("  Cabin  ", "100.00", alice, "EQUAL", people(alice, bob, carol))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Cabin"))
                .andExpect(jsonPath("$.amount").value(100.0))
                .andExpect(jsonPath("$.splitType").value("EQUAL"))
                .andExpect(jsonPath("$.paidBy.userId").value(alice.id()))
                .andExpect(jsonPath("$.createdBy.userId").value(bob.id()))
                .andExpect(jsonPath("$.shares.length()").value(3))
                .andExpect(jsonPath("$.shares[0].userId").value(alice.id()))
                .andExpect(jsonPath("$.shares[0].owed").value(33.34))
                .andExpect(jsonPath("$.shares[1].owed").value(33.33))
                .andExpect(jsonPath("$.shares[2].owed").value(33.33))
                .andExpect(jsonPath("$.shares[0].value").doesNotExist())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void storesWhatWasEnteredForEachSplitType() throws Exception {
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Groceries", "80", alice, "PERCENT", weighted(alice, 50, bob, 25, carol, 25))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares[0].value").value(50.0))
                .andExpect(jsonPath("$.shares[0].owed").value(40.0))
                .andExpect(jsonPath("$.shares[2].owed").value(20.0));

        mockMvc.perform(postExpense(alice, groupId, expenseJson("Taxi", "30", bob, "SHARES", weighted(bob, 2, carol, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares[0].value").value(2.0))
                .andExpect(jsonPath("$.shares[0].owed").value(20.0))
                .andExpect(jsonPath("$.shares[1].owed").value(10.0));

        mockMvc.perform(postExpense(alice, groupId, expenseJson("Lift passes", "150.50", carol, "EXACT", weighted(alice, 100.5, carol, 50))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares[0].owed").value(100.5))
                .andExpect(jsonPath("$.shares[1].owed").value(50.0));
    }

    @Test
    void rejectsSplitsThatDontAddUp() throws Exception {
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "80", alice, "PERCENT", weighted(alice, 50, bob, 40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The percentages add up to 90, not 100"));
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "80", alice, "EXACT", weighted(alice, 50, bob, 40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The amounts add up to 90.00, but the total is 80.00"));
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "80", alice, "SHARES", people(alice, bob))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Enter the number of shares for everyone in the split"));

        mockMvc.perform(as(alice, get(path()))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void everyoneInvolvedMustBeAMemberOnce() throws Exception {
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "10", alice, "EQUAL", people(alice, outsider))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Everyone in the split must be a member of the group"));
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "10", outsider, "EQUAL", people(alice))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The person who paid must be a member of the group"));
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "10", alice, "EQUAL", people(alice, bob, alice))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Test User is in the split more than once"));
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(postExpense(alice, groupId, expenseJson(" ", "10.001", alice, "EQUAL", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.participants").exists());
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "10", alice, "EXACT", weighted(alice, -10, bob, 20))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['participants[0].value']").exists());
        mockMvc.perform(postExpense(alice, groupId, expenseJson("Bad", "10", alice, "HALVES", people(alice))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewWorksOutSharesWithoutSaving() throws Exception {
        mockMvc.perform(withJson(post(path() + "/preview"), bob.token(), """
                        {"amount": 10, "splitType": "EQUAL", "participants": %s}
                        """.formatted(people(carol, bob, alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].userId").value(carol.id()))
                .andExpect(jsonPath("$[0].displayName").value("Test User"))
                .andExpect(jsonPath("$[0].owed").value(3.34))
                .andExpect(jsonPath("$[1].owed").value(3.33));

        mockMvc.perform(withJson(post(path() + "/preview"), bob.token(), """
                        {"amount": 10, "splitType": "PERCENT", "participants": %s}
                        """.formatted(weighted(bob, 30, carol, 30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The percentages add up to 60, not 100"));

        mockMvc.perform(as(alice, get(path()))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listsNewestFirstWithSharesAndPaging() throws Exception {
        addExpense(alice, groupId, expenseJson("Older", "10", alice, "EQUAL", people(alice, bob)).replace("2026-09-20", "2026-09-01"));
        addEqualExpense(groupId, bob, "30", alice, bob, carol);
        addEqualExpense(groupId, carol, "20", bob, carol);

        mockMvc.perform(as(bob, get(path()).param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                // Same date: the most recently added comes first.
                .andExpect(jsonPath("$.content[0].paidBy.userId").value(carol.id()))
                .andExpect(jsonPath("$.content[0].shares.length()").value(2))
                .andExpect(jsonPath("$.content[1].paidBy.userId").value(bob.id()))
                .andExpect(jsonPath("$.content[1].shares.length()").value(3));
        mockMvc.perform(as(bob, get(path()).param("page", "1").param("size", "2")))
                .andExpect(jsonPath("$.content[0].description").value("Older"));
    }

    @Test
    void onlyTheCreatorOrAnOwnerCanChangeAnExpense() throws Exception {
        String json = expenseJson("Fuel", "60", bob, "EQUAL", people(alice, bob, carol));
        String expenseId = addExpense(bob, groupId, json);

        // Carol is in the split but didn't add it and isn't an owner.
        mockMvc.perform(as(carol, get(path() + "/" + expenseId))).andExpect(jsonPath("$.canEdit").value(false));
        mockMvc.perform(as(alice, get(path() + "/" + expenseId))).andExpect(jsonPath("$.canEdit").value(true));
        mockMvc.perform(putExpense(carol, expenseId, withVersion(json, 0))).andExpect(status().isForbidden());
        mockMvc.perform(as(carol, delete(path() + "/" + expenseId))).andExpect(status().isForbidden());

        mockMvc.perform(putExpense(bob, expenseId, withVersion(json.replace("Fuel", "Petrol"), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Petrol"));
        mockMvc.perform(as(alice, delete(path() + "/" + expenseId))).andExpect(status().isNoContent());
        mockMvc.perform(as(bob, get(path() + "/" + expenseId))).andExpect(status().isNotFound());
    }

    @Test
    void nonMembersGetNotFound() throws Exception {
        String expenseId = addEqualExpense(groupId, alice, "10", alice, bob);
        mockMvc.perform(as(outsider, get(path()))).andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, get(path() + "/" + expenseId))).andExpect(status().isNotFound());
        mockMvc.perform(postExpense(outsider, groupId, expenseJson("X", "10", outsider, "EQUAL", people(outsider))))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, delete(path() + "/" + expenseId))).andExpect(status().isNotFound());

        // An expense can't be reached through a different group, even one the caller belongs to.
        String otherGroup = createGroup(alice, "Other");
        mockMvc.perform(as(alice, get("/api/groups/" + otherGroup + "/expenses/" + expenseId))).andExpect(status().isNotFound());
        mockMvc.perform(as(alice, get(path() + "/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void staleEditsAreRejected() throws Exception {
        String json = expenseJson("Hotel", "90", alice, "EQUAL", people(alice, bob, carol));
        String expenseId = addExpense(alice, groupId, json);

        mockMvc.perform(putExpense(alice, expenseId, json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("version is required when updating an expense"));
        mockMvc.perform(putExpense(alice, expenseId, withVersion(json, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(putExpense(alice, expenseId, withVersion(json, 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void changingOnlyTheSplitStillBumpsTheVersion() throws Exception {
        String expenseId = addExpense(alice, groupId, expenseJson("Hotel", "90", alice, "SHARES", weighted(alice, 1, bob, 2)));

        mockMvc.perform(putExpense(alice, expenseId, withVersion(expenseJson("Hotel", "90", alice, "SHARES", weighted(alice, 2, bob, 1)), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.shares[0].owed").value(60.0));
        mockMvc.perform(putExpense(alice, expenseId, withVersion(expenseJson("Hotel", "90", alice, "SHARES", weighted(alice, 5, bob, 1)), 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void editsCanReorderAddAndRemoveParticipants() throws Exception {
        String expenseId = addEqualExpense(groupId, alice, "90", alice, bob);

        // Carol is added, Alice is removed and Bob moves: shares are matched by person, so no duplicates.
        mockMvc.perform(putExpense(alice, expenseId, withVersion(expenseJson("Dinner", "90", alice, "EXACT", weighted(carol, 60, bob, 30)), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value("EXACT"))
                .andExpect(jsonPath("$.shares.length()").value(2))
                .andExpect(jsonPath("$.shares[0].userId").value(carol.id()))
                .andExpect(jsonPath("$.shares[0].value").value(60.0))
                .andExpect(jsonPath("$.shares[1].userId").value(bob.id()));

        List<String> ids = read(mockMvc.perform(as(alice, get(path() + "/" + expenseId))).andReturn(), "$.shares[*].userId");
        assertThat(ids).containsExactly(carol.id(), bob.id());
    }
}
