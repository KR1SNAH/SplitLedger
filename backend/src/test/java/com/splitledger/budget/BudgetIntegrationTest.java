package com.splitledger.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.splitledger.IntegrationTest;

class BudgetIntegrationTest extends IntegrationTest {

    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        user = newUser();
    }

    private ResultActions postBudget(TestUser owner, String json) throws Exception {
        return mockMvc.perform(withJson(post("/api/budgets"), owner.token(), json));
    }

    private String createBudget(TestUser owner, String categoryId, String amount) throws Exception {
        String category = categoryId == null ? "" : "\"categoryId\": \"%s\", ".formatted(categoryId);
        return read(postBudget(owner, "{" + category + "\"amount\": " + amount + "}")
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void addExpense(TestUser owner, String categoryId, String amount, String date) throws Exception {
        mockMvc.perform(withJson(post("/api/expenses"), owner.token(), """
                        {"description": "Something", "amount": %s, "expenseDate": "%s", "categoryId": %s}
                        """.formatted(amount, date, categoryId == null ? "null" : "\"" + categoryId + "\"")))
                .andExpect(status().isCreated());
    }

    private ResultActions budgets(TestUser owner, String month) throws Exception {
        return mockMvc.perform(as(owner, get("/api/budgets?month=" + month)));
    }

    @Test
    void reportsSpendingAgainstEachBudgetForTheMonth() throws Exception {
        String groceries = categoryId(user.token(), "Groceries");
        String transport = categoryId(user.token(), "Transport");
        createBudget(user, groceries, "400.00");
        createBudget(user, transport, "50");
        createBudget(user, null, "1000");

        addExpense(user, groceries, "120.50", "2026-09-03");
        addExpense(user, groceries, "30.25", "2026-09-30");
        addExpense(user, transport, "60.00", "2026-09-10");
        addExpense(user, null, "15.00", "2026-09-12"); // uncategorized: only counts towards the overall budget
        addExpense(user, groceries, "999.00", "2026-08-31"); // a different month
        addExpense(newUser(), null, "500.00", "2026-09-05"); // someone else's

        budgets(user, "2026-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Overall first, then by category name.
                .andExpect(jsonPath("$[0].category").doesNotExist())
                .andExpect(jsonPath("$[0].amount").value(1000.0))
                .andExpect(jsonPath("$[0].spent").value(225.75))
                .andExpect(jsonPath("$[0].remaining").value(774.25))
                .andExpect(jsonPath("$[0].percentUsed").value(22))
                .andExpect(jsonPath("$[0].overBudget").value(false))
                .andExpect(jsonPath("$[1].category.name").value("Groceries"))
                .andExpect(jsonPath("$[1].spent").value(150.75))
                .andExpect(jsonPath("$[1].percentUsed").value(37))
                .andExpect(jsonPath("$[2].category.name").value("Transport"))
                .andExpect(jsonPath("$[2].spent").value(60.0))
                .andExpect(jsonPath("$[2].remaining").value(-10.0))
                .andExpect(jsonPath("$[2].percentUsed").value(120))
                .andExpect(jsonPath("$[2].overBudget").value(true));

        // Budgets recur: the same limits apply to a month with no spending.
        budgets(user, "2026-10")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].spent").value(0.0))
                .andExpect(jsonPath("$[1].remaining").value(400.0))
                .andExpect(jsonPath("$[1].overBudget").value(false));
    }

    @Test
    void spendingExactlyTheLimitIsNotOverBudget() throws Exception {
        createBudget(user, null, "100");
        addExpense(user, null, "100.00", "2026-09-01");

        budgets(user, "2026-09")
                .andExpect(jsonPath("$[0].remaining").value(0.0))
                .andExpect(jsonPath("$[0].percentUsed").value(100))
                .andExpect(jsonPath("$[0].overBudget").value(false));
    }

    @Test
    void onlyOneBudgetPerCategoryAndOneOverall() throws Exception {
        String groceries = categoryId(user.token(), "Groceries");
        createBudget(user, groceries, "400");
        createBudget(user, null, "1000");

        postBudget(user, """
                {"categoryId": "%s", "amount": 10}
                """.formatted(groceries))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You already have a budget for Groceries"));
        postBudget(user, """
                {"amount": 10}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You already have an overall budget"));

        // Another user can budget for the same built-in category.
        createBudget(newUser(), groceries, "50");
    }

    @Test
    void validatesTheAmountAndCategory() throws Exception {
        postBudget(user, """
                {"amount": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        postBudget(user, """
                {"amount": 10.001}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        // Someone else's custom category can't be budgeted for.
        TestUser other = newUser();
        String theirs = read(mockMvc.perform(withJson(post("/api/categories"), other.token(), """
                        {"name": "Private", "color": "#123456"}
                        """)).andReturn(), "$.id");
        postBudget(user, """
                {"categoryId": "%s", "amount": 10}
                """.formatted(theirs))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown category"));

        budgets(user, "2026-13").andExpect(status().isBadRequest());
    }

    @Test
    void updatesAndDeletesOnlyYourOwnBudgets() throws Exception {
        String budgetId = createBudget(user, null, "1000");
        TestUser other = newUser();

        mockMvc.perform(withJson(put("/api/budgets/" + budgetId), other.token(), """
                        {"amount": 1}
                        """))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(other, delete("/api/budgets/" + budgetId))).andExpect(status().isNotFound());
        budgets(other, "2026-09").andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(withJson(put("/api/budgets/" + budgetId), user.token(), """
                        {"amount": 750.50}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(750.5));
        budgets(user, "2026-09").andExpect(jsonPath("$[0].amount").value(750.5));

        mockMvc.perform(as(user, delete("/api/budgets/" + budgetId))).andExpect(status().isNoContent());
        budgets(user, "2026-09").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingACustomCategoryDeletesItsBudget() throws Exception {
        String categoryId = read(mockMvc.perform(withJson(post("/api/categories"), user.token(), """
                        {"name": "Hobbies", "color": "#123456"}
                        """)).andReturn(), "$.id");
        createBudget(user, categoryId, "80");
        createBudget(user, null, "1000");

        mockMvc.perform(as(user, delete("/api/categories/" + categoryId))).andExpect(status().isNoContent());

        List<Map<String, Object>> remaining = read(budgets(user, "2026-09").andReturn(), "$");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().get("category")).isNull();
    }
}
