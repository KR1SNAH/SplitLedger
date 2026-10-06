package com.splitledger.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.splitledger.IntegrationTest;

/** Helpers for building group expense and settlement requests. */
abstract class LedgerTestSupport extends IntegrationTest {

    /** Participants for an EQUAL split. */
    static String people(TestUser... users) {
        return Arrays.stream(users)
                .map(u -> "{\"userId\": \"%s\"}".formatted(u.id()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /** Participants with values, given as user, value, user, value... */
    static String weighted(Object... userAndValue) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < userAndValue.length; i += 2) {
            entries.add("{\"userId\": \"%s\", \"value\": %s}".formatted(((TestUser) userAndValue[i]).id(), userAndValue[i + 1]));
        }
        return "[" + String.join(", ", entries) + "]";
    }

    static String expenseJson(String description, String amount, TestUser paidBy, String splitType, String participants) {
        return """
                {"description": "%s", "amount": %s, "expenseDate": "2026-09-20", "paidBy": "%s",
                 "splitType": "%s", "participants": %s}
                """.formatted(description, amount, paidBy.id(), splitType, participants);
    }

    /** Adds {@code "version": n} to an expense JSON object, for updates. */
    static String withVersion(String json, long version) {
        return json.strip().replaceFirst("}$", ", \"version\": " + version + "}");
    }

    MockHttpServletRequestBuilder postExpense(TestUser actor, String groupId, String json) {
        return withJson(post("/api/groups/" + groupId + "/expenses"), actor.token(), json);
    }

    /** Adds an expense and returns its id. */
    String addExpense(TestUser actor, String groupId, String json) throws Exception {
        return read(mockMvc.perform(postExpense(actor, groupId, json)).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    /** An EQUAL split of {@code amount}, paid by {@code paidBy}, between {@code participants}. */
    String addEqualExpense(String groupId, TestUser paidBy, String amount, TestUser... participants) throws Exception {
        return addExpense(paidBy, groupId, expenseJson("Dinner", amount, paidBy, "EQUAL", people(participants)));
    }

    MockHttpServletRequestBuilder postSettlement(TestUser actor, String groupId, TestUser from, TestUser to, String amount) {
        return withJson(post("/api/groups/" + groupId + "/settlements"), actor.token(), """
                {"fromUserId": "%s", "toUserId": "%s", "amount": %s, "settledOn": "2026-09-21"}
                """.formatted(from.id(), to.id(), amount));
    }

    String settle(TestUser actor, String groupId, TestUser from, TestUser to, String amount) throws Exception {
        return read(mockMvc.perform(postSettlement(actor, groupId, from, to, amount))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }
}
