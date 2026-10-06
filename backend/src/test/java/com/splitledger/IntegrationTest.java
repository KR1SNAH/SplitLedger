package com.splitledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Base for full-stack tests: real Spring context, MockMvc and a Testcontainers Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    /** A registered user: their bearer access token, id and email. */
    protected record TestUser(String token, String id, String email) {
    }

    /** Registers a fresh user and returns their bearer access token. */
    protected String registerUser() throws Exception {
        return newUser().token();
    }

    protected TestUser newUser() throws Exception {
        return newUser("user-" + UUID.randomUUID() + "@example.com");
    }

    protected TestUser newUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "displayName": "Test User"}
                                """.formatted(email)))
                .andReturn();
        return testUser(result);
    }

    /** The user from a register or login response. */
    protected static TestUser testUser(MvcResult authResponse) throws Exception {
        String body = authResponse.getResponse().getContentAsString();
        return new TestUser(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.user.id"), JsonPath.read(body, "$.user.email"));
    }

    protected static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String token, String json) {
        return request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    protected static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    protected static MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + user.token());
    }

    /** Creates a group owned by {@code owner} and returns its id. */
    protected String createGroup(TestUser owner, String name) throws Exception {
        return read(mockMvc.perform(withJson(post("/api/groups"), owner.token(), """
                        {"name": "%s"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    /** Adds {@code member} to the group through an invite link created by {@code owner}. */
    protected void addMember(TestUser owner, String groupId, TestUser member) throws Exception {
        String token = read(mockMvc.perform(as(owner, post("/api/groups/" + groupId + "/invite-link")))
                .andExpect(status().isCreated())
                .andReturn(), "$.token");
        mockMvc.perform(withJson(post("/api/invite-links/accept"), member.token(), """
                        {"token": "%s"}
                        """.formatted(token)))
                .andExpect(status().isOk());
    }

    /** The categories visible to the user, as returned by GET /api/categories. */
    protected List<Map<String, Object>> categories(String token) throws Exception {
        return read(mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token)).andReturn(), "$");
    }

    protected Map<String, Object> category(String token, String name) throws Exception {
        return categories(token).stream()
                .filter(c -> name.equals(c.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No category named " + name));
    }

    protected String categoryId(String token, String name) throws Exception {
        return (String) category(token, name).get("id");
    }
}
