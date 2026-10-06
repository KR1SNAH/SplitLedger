package com.splitledger.expense;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.splitledger.IntegrationTest;

class MonthlyTotalsIntegrationTest extends IntegrationTest {

    private void addExpense(TestUser owner, String amount, String date) throws Exception {
        mockMvc.perform(withJson(post("/api/expenses"), owner.token(), """
                        {"description": "Something", "amount": %s, "expenseDate": "%s"}
                        """.formatted(amount, date)))
                .andExpect(status().isCreated());
    }

    @Test
    void totalsPerMonthOldestFirstWithEmptyMonthsAsZero() throws Exception {
        TestUser user = newUser();
        addExpense(user, "10.00", "2026-06-01");
        addExpense(user, "5.50", "2026-06-30");
        addExpense(user, "20.00", "2026-09-15");
        addExpense(user, "99.00", "2026-10-01"); // after the range
        addExpense(user, "99.00", "2026-05-31"); // before the range
        addExpense(newUser(), "42.00", "2026-09-15"); // someone else's

        mockMvc.perform(as(user, get("/api/expenses/monthly?to=2026-09&months=4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].month").value("2026-06"))
                .andExpect(jsonPath("$[0].total").value(15.5))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].month").value("2026-07"))
                .andExpect(jsonPath("$[1].total").value(0.0))
                .andExpect(jsonPath("$[1].count").value(0))
                .andExpect(jsonPath("$[2].month").value("2026-08"))
                .andExpect(jsonPath("$[3].month").value("2026-09"))
                .andExpect(jsonPath("$[3].total").value(20.0));
    }

    @Test
    void spansYearBoundaries() throws Exception {
        TestUser user = newUser();
        addExpense(user, "12.00", "2025-12-24");
        addExpense(user, "8.00", "2026-01-02");

        mockMvc.perform(as(user, get("/api/expenses/monthly?to=2026-01&months=2")))
                .andExpect(jsonPath("$[0].month").value("2025-12"))
                .andExpect(jsonPath("$[0].total").value(12.0))
                .andExpect(jsonPath("$[1].month").value("2026-01"))
                .andExpect(jsonPath("$[1].total").value(8.0));
    }

    @Test
    void limitsTheNumberOfMonths() throws Exception {
        TestUser user = newUser();
        mockMvc.perform(as(user, get("/api/expenses/monthly?months=25"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(user, get("/api/expenses/monthly?months=0"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(user, get("/api/expenses/monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }
}
