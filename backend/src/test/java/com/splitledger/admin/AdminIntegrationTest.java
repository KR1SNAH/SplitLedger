package com.splitledger.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.splitledger.IntegrationTest;
import com.splitledger.auth.AuthController;

import jakarta.servlet.http.Cookie;

class AdminIntegrationTest extends IntegrationTest {

    private TestUser admin;

    @BeforeEach
    void setUp() throws Exception {
        // Seeded from app.admin.* defaults in application.yml.
        admin = testUser(login("admin@splitledger.dev", "admin12345").andExpect(status().isOk()).andReturn());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie));
    }

    private ResultActions setEnabled(TestUser actor, String userId, boolean enabled) throws Exception {
        return mockMvc.perform(as(actor, post("/api/admin/users/" + userId + (enabled ? "/enable" : "/disable"))));
    }

    @Test
    void disablingSignsTheUserOutEverywhereImmediately() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "displayName": "Test User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        TestUser user = testUser(registered);
        Cookie refreshCookie = registered.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        mockMvc.perform(as(user, get("/api/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        setEnabled(admin, user.id(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // The access token they already hold stops working at once, not when it expires.
        mockMvc.perform(as(user, get("/api/users/me"))).andExpect(status().isUnauthorized());
        // Their sessions were revoked, so they can't get a new one...
        refresh(refreshCookie).andExpect(status().isUnauthorized());
        // ...or sign in again. A wrong password still gets the generic error.
        login(email, "password123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This account has been disabled. Contact an administrator."));
        login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        setEnabled(admin, user.id(), true).andExpect(jsonPath("$.enabled").value(true));
        String newToken = read(login(email, "password123").andExpect(status().isOk()).andReturn(), "$.accessToken");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
    }

    @Test
    void enablingAndDisablingAreIdempotent() throws Exception {
        TestUser user = newUser();
        setEnabled(admin, user.id(), true).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        setEnabled(admin, user.id(), false).andExpect(status().isOk());
        setEnabled(admin, user.id(), false).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void adminsCantDisableThemselves() throws Exception {
        setEnabled(admin, admin.id(), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You can't disable your own account"));
        mockMvc.perform(as(admin, get("/api/users/me"))).andExpect(status().isOk());
    }

    @Test
    void unknownUsersAreNotFound() throws Exception {
        setEnabled(admin, UUID.randomUUID().toString(), false).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanManageUsersOrSeeStats() throws Exception {
        TestUser user = newUser();
        TestUser victim = newUser();
        setEnabled(user, victim.id(), false).andExpect(status().isForbidden());
        mockMvc.perform(as(user, get("/api/admin/stats"))).andExpect(status().isForbidden());
        mockMvc.perform(as(victim, get("/api/users/me"))).andExpect(status().isOk());
    }

    @Test
    void userListShowsAccountStatus() throws Exception {
        TestUser user = newUser();
        setEnabled(admin, user.id(), false);

        // Newest first, so the user just created is on the first page.
        List<Map<String, Object>> users = read(mockMvc.perform(as(admin, get("/api/admin/users?size=100"))).andReturn(), "$.content");
        Map<String, Object> row = users.stream().filter(u -> user.id().equals(u.get("id"))).findFirst().orElseThrow();
        assertThat(row.get("enabled")).isEqualTo(false);
    }

    @Test
    void statsCountUsersAndActivity() throws Exception {
        Map<String, Object> before = read(mockMvc.perform(as(admin, get("/api/admin/stats"))).andReturn(), "$");

        TestUser user = newUser();
        mockMvc.perform(withJson(post("/api/expenses"), user.token(), """
                        {"description": "Lunch", "amount": 12.00, "expenseDate": "2026-09-20"}
                        """))
                .andExpect(status().isCreated());
        setEnabled(admin, newUser().id(), false);

        MvcResult result = mockMvc.perform(as(admin, get("/api/admin/stats"))).andExpect(status().isOk()).andReturn();
        Map<String, Object> after = read(result, "$");

        assertThat(delta(before, after, "users", "total")).isEqualTo(2);
        assertThat(delta(before, after, "users", "newLast30Days")).isEqualTo(2);
        assertThat(delta(before, after, "users", "activeLast30Days")).isEqualTo(2);
        assertThat(delta(before, after, "users", "disabled")).isEqualTo(1);
        assertThat(delta(before, after, "users", "admins")).isZero();
        assertThat(delta(before, after, "content", "personalExpenses")).isEqualTo(1);

        List<Map<String, Object>> monthlyBefore = cast(before.get("monthly"));
        List<Map<String, Object>> monthlyAfter = cast(after.get("monthly"));
        assertThat(monthlyAfter).hasSize(6);
        Map<String, Object> thisMonth = monthlyAfter.getLast();
        assertThat(thisMonth.get("month")).isEqualTo(YearMonth.now(ZoneOffset.UTC).toString());
        assertThat(number(thisMonth, "newUsers") - number(monthlyBefore.getLast(), "newUsers")).isEqualTo(2);
        assertThat(number(thisMonth, "newExpenses") - number(monthlyBefore.getLast(), "newExpenses")).isEqualTo(1);
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String section, String field) {
        return number(cast(after.get(section)), field) - number(cast(before.get(section)), field);
    }

    private static long number(Map<String, Object> map, String field) {
        return ((Number) map.get(field)).longValue();
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
