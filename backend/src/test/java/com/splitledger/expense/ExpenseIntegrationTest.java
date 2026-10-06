package com.splitledger.expense;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.splitledger.IntegrationTest;

class ExpenseIntegrationTest extends IntegrationTest {

    private String token;
    private String groceriesId;
    private String transportId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        groceriesId = categoryId(token, "Groceries");
        transportId = categoryId(token, "Transport");
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String createExpense(String description, String amount, String date, String categoryId) throws Exception {
        String category = categoryId == null ? "null" : "\"" + categoryId + "\"";
        return read(mockMvc.perform(withJson(post("/api/expenses"), token, """
                        {"description": "%s", "amount": %s, "expenseDate": "%s", "categoryId": %s}
                        """.formatted(description, amount, date, category)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    @Test
    void createAndFetchExpense() throws Exception {
        String id = createExpense("Weekly shop", "84.30", "2026-09-10", groceriesId);

        mockMvc.perform(auth(get("/api/expenses/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Weekly shop"))
                .andExpect(jsonPath("$.amount").value(84.30))
                .andExpect(jsonPath("$.expenseDate").value("2026-09-10"))
                .andExpect(jsonPath("$.category.name").value("Groceries"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void rejectsInvalidAmounts() throws Exception {
        for (String amount : new String[] {"0", "-5", "10.999", "99999999999"}) {
            mockMvc.perform(withJson(post("/api/expenses"), token, """
                            {"description": "Bad", "amount": %s, "expenseDate": "2026-09-10"}
                            """.formatted(amount)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }
    }

    @Test
    void rejectsAnotherUsersCategory() throws Exception {
        String other = registerUser();
        String privateCategory = read(mockMvc.perform(withJson(post("/api/categories"), other, """
                        {"name": "Secret", "color": "#000000"}
                        """)).andReturn(), "$.id");

        mockMvc.perform(withJson(post("/api/expenses"), token, """
                        {"description": "Sneaky", "amount": 1, "expenseDate": "2026-09-10", "categoryId": "%s"}
                        """.formatted(privateCategory)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expensesArePrivate() throws Exception {
        String id = createExpense("Mine", "10", "2026-09-10", null);
        String other = registerUser();

        mockMvc.perform(get("/api/expenses/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/expenses/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/expenses").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void updateRequiresCurrentVersion() throws Exception {
        String id = createExpense("Taxi", "12.00", "2026-09-10", transportId);
        String update = """
                {"description": "Taxi to airport", "amount": 18.50, "expenseDate": "2026-09-10", "version": %d}
                """;

        mockMvc.perform(withJson(put("/api/expenses/" + id), token, update.formatted(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Taxi to airport"))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));

        // A second client still holding version 0 must not overwrite the change.
        mockMvc.perform(withJson(put("/api/expenses/" + id), token, update.formatted(0)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRemovesExpense() throws Exception {
        String id = createExpense("Oops", "1", "2026-09-10", null);
        mockMvc.perform(auth(delete("/api/expenses/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/expenses/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void listFiltersSortsAndPaginates() throws Exception {
        createExpense("Bus pass", "40", "2026-08-01", transportId);
        createExpense("Supermarket 100%", "55.25", "2026-09-05", groceriesId);
        createExpense("Farmers market", "20", "2026-09-12", groceriesId);
        createExpense("Train", "30", "2026-09-20", transportId);

        // Default sort: newest date first.
        mockMvc.perform(auth(get("/api/expenses")))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].description").value("Train"));

        mockMvc.perform(auth(get("/api/expenses").param("from", "2026-09-01").param("to", "2026-09-30")))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(auth(get("/api/expenses").param("categoryId", groceriesId).param("sort", "amount,asc")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].description").value("Farmers market"));

        // Text search is case-insensitive and treats % literally.
        mockMvc.perform(auth(get("/api/expenses").param("q", "MARKET")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(auth(get("/api/expenses").param("q", "100%")))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(auth(get("/api/expenses").param("size", "3").param("page", "1")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void rejectsBadQueryParameters() throws Exception {
        mockMvc.perform(auth(get("/api/expenses").param("sort", "password")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/api/expenses").param("from", "2026-09-30").param("to", "2026-09-01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryTotalsByCategory() throws Exception {
        createExpense("Shop 1", "10.10", "2026-09-01", groceriesId);
        createExpense("Shop 2", "20.20", "2026-09-02", groceriesId);
        createExpense("Bus", "5.00", "2026-09-03", transportId);
        createExpense("Misc", "1.00", "2026-09-04", null);
        createExpense("Last month", "999", "2026-08-15", groceriesId);

        mockMvc.perform(auth(get("/api/expenses/summary").param("from", "2026-09-01").param("to", "2026-09-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(36.30))
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.byCategory[0].name").value("Groceries"))
                .andExpect(jsonPath("$.byCategory[0].total").value(30.30))
                .andExpect(jsonPath("$.byCategory[0].count").value(2))
                .andExpect(jsonPath("$.byCategory[2].categoryId").doesNotExist());
    }
}
