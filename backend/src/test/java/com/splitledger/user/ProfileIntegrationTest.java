package com.splitledger.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.splitledger.IntegrationTest;

class ProfileIntegrationTest extends IntegrationTest {

    @Test
    void newUsersDefaultToUsdAndCanChangeProfile() throws Exception {
        String token = registerUser();
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.currency").value("USD"));

        mockMvc.perform(withJson(put("/api/users/me"), token, """
                        {"displayName": "  Priya  ", "currency": "INR"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Priya"))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void rejectsUnknownCurrency() throws Exception {
        String token = registerUser();
        mockMvc.perform(withJson(put("/api/users/me"), token, """
                        {"displayName": "Priya", "currency": "XYZ"}
                        """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withJson(put("/api/users/me"), token, """
                        {"displayName": "Priya", "currency": "rupees"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }
}
