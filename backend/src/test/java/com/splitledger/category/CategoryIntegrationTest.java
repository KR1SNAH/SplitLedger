package com.splitledger.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.splitledger.IntegrationTest;

class CategoryIntegrationTest extends IntegrationTest {

    private String createCategory(String token, String name) throws Exception {
        return read(mockMvc.perform(withJson(post("/api/categories"), token, """
                        {"name": "%s", "color": "#123ABC"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    @Test
    void listsBuiltInCategoriesForNewUser() throws Exception {
        String token = registerUser();
        assertThat(categories(token)).hasSize(11);
        assertThat(category(token, "Groceries")).containsEntry("system", true);
    }

    @Test
    void customCategoriesArePrivateToTheirOwner() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        createCategory(alice, "Pets");

        assertThat(categories(alice)).hasSize(12);
        assertThat(category(alice, "Pets")).containsEntry("color", "#123abc").containsEntry("system", false);
        assertThat(categories(bob)).hasSize(11);
    }

    @Test
    void namesMustBeUniqueIgnoringCase() throws Exception {
        String token = registerUser();
        createCategory(token, "Pets");

        mockMvc.perform(withJson(post("/api/categories"), token, """
                        {"name": "pets", "color": "#000000"}
                        """))
                .andExpect(status().isConflict());
        // Clashing with a built-in name is also rejected.
        mockMvc.perform(withJson(post("/api/categories"), token, """
                        {"name": "GROCERIES", "color": "#000000"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void builtInCategoriesCannotBeModified() throws Exception {
        String token = registerUser();
        String groceriesId = categoryId(token, "Groceries");

        mockMvc.perform(withJson(put("/api/categories/" + groceriesId), token, """
                        {"name": "Food", "color": "#000000"}
                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/categories/" + groceriesId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherUsersCannotTouchYourCategories() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        String id = createCategory(alice, "Pets");

        mockMvc.perform(delete("/api/categories/" + id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingCategoryLeavesExpensesUncategorized() throws Exception {
        String token = registerUser();
        String categoryId = createCategory(token, "Pets");
        String expenseId = read(mockMvc.perform(withJson(post("/api/expenses"), token, """
                        {"description": "Dog food", "amount": 25.00, "expenseDate": "2026-09-01", "categoryId": "%s"}
                        """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(delete("/api/categories/" + categoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses/" + expenseId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").doesNotExist());
    }

    @Test
    void invalidColorIsRejected() throws Exception {
        String token = registerUser();
        mockMvc.perform(withJson(post("/api/categories"), token, """
                        {"name": "Pets", "color": "red"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.color").exists());
    }
}
