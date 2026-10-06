package com.splitledger.group.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.splitledger.IntegrationTest;

class InviteIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser owner;
    private TestUser friend;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newUser();
        friend = newUser();
        groupId = createGroup(owner, "Ski trip");
    }

    private String createLink() throws Exception {
        return read(mockMvc.perform(as(owner, post("/api/groups/" + groupId + "/invite-link")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn(), "$.token");
    }

    private ResultActions linkAction(TestUser user, String action, String token) throws Exception {
        return mockMvc.perform(withJson(post("/api/invite-links/" + action), user.token(), """
                {"token": "%s"}
                """.formatted(token)));
    }

    private ResultActions inviteByEmail(String email) throws Exception {
        return mockMvc.perform(withJson(post("/api/groups/" + groupId + "/invites"), owner.token(), """
                {"email": "%s"}
                """.formatted(email)));
    }

    private String myFirstInviteId(TestUser user) throws Exception {
        return read(mockMvc.perform(as(user, get("/api/invites"))).andReturn(), "$[0].id");
    }

    // --- Invite links ---

    @Test
    void joinWithInviteLink() throws Exception {
        String token = createLink();

        linkAction(friend, "preview", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.groupName").value("Ski trip"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.alreadyMember").value(false));

        linkAction(friend, "accept", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members.length()").value(2))
                // Members don't see the invite link status; only owners manage it.
                .andExpect(jsonPath("$.inviteLink").doesNotExist());

        // Accepting again is a harmless no-op.
        linkAction(friend, "accept", token).andExpect(status().isOk());
        linkAction(friend, "preview", token).andExpect(jsonPath("$.alreadyMember").value(true));

        mockMvc.perform(as(owner, get("/api/groups/" + groupId)))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.inviteLink.expiresAt").exists());
    }

    @Test
    void onlyTheHashOfTheLinkIsStored() throws Exception {
        String token = createLink();
        String stored = jdbc.queryForObject(
                "select invite_token_hash from groups where id = ?", String.class, UUID.fromString(groupId));
        assertThat(stored).hasSize(64).isNotEqualTo(token);
    }

    @Test
    void regeneratingOrDisablingTheLinkInvalidatesIt() throws Exception {
        String first = createLink();
        String second = createLink();
        linkAction(friend, "preview", first).andExpect(status().isNotFound());
        linkAction(friend, "preview", second).andExpect(status().isOk());

        mockMvc.perform(as(owner, delete("/api/groups/" + groupId + "/invite-link"))).andExpect(status().isNoContent());
        linkAction(friend, "accept", second).andExpect(status().isNotFound());
        mockMvc.perform(as(owner, get("/api/groups/" + groupId))).andExpect(jsonPath("$.inviteLink").doesNotExist());
    }

    @Test
    void expiredLinksDontWork() throws Exception {
        String token = createLink();
        jdbc.update("update groups set invite_expires_at = now() - interval '1 minute' where id = ?", UUID.fromString(groupId));

        linkAction(friend, "preview", token).andExpect(status().isNotFound());
        linkAction(friend, "accept", token).andExpect(status().isNotFound());
        mockMvc.perform(as(owner, get("/api/groups/" + groupId))).andExpect(jsonPath("$.inviteLink").doesNotExist());
    }

    @Test
    void garbageTokensAreRejected() throws Exception {
        linkAction(friend, "accept", "not-a-real-token").andExpect(status().isNotFound());
        linkAction(friend, "accept", "").andExpect(status().isBadRequest());
    }

    @Test
    void onlyOwnersManageInvites() throws Exception {
        addMember(owner, groupId, friend);
        String base = "/api/groups/" + groupId;

        mockMvc.perform(as(friend, post(base + "/invite-link"))).andExpect(status().isForbidden());
        mockMvc.perform(as(friend, delete(base + "/invite-link"))).andExpect(status().isForbidden());
        mockMvc.perform(as(friend, get(base + "/invites"))).andExpect(status().isForbidden());
        mockMvc.perform(withJson(post(base + "/invites"), friend.token(), """
                        {"email": "someone@example.com"}
                        """))
                .andExpect(status().isForbidden());

        TestUser outsider = newUser();
        mockMvc.perform(as(outsider, post(base + "/invite-link"))).andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, get(base + "/invites"))).andExpect(status().isNotFound());
    }

    // --- Email invites ---

    @Test
    void acceptEmailInvite() throws Exception {
        // Emails are matched case-insensitively.
        inviteByEmail(friend.email().toUpperCase(Locale.ROOT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(friend.email()))
                .andExpect(jsonPath("$.invitedBy").value("Test User"));

        mockMvc.perform(as(owner, get("/api/groups/" + groupId + "/invites")))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(friend, get("/api/invites")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupId").value(groupId))
                .andExpect(jsonPath("$[0].groupName").value("Ski trip"));

        mockMvc.perform(as(friend, post("/api/invites/" + myFirstInviteId(friend) + "/accept")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));

        mockMvc.perform(as(friend, get("/api/invites"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(owner, get("/api/groups/" + groupId + "/invites"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsDuplicateInvitesAndExistingMembers() throws Exception {
        inviteByEmail(friend.email()).andExpect(status().isCreated());
        inviteByEmail(friend.email()).andExpect(status().isConflict());
        inviteByEmail(owner.email()).andExpect(status().isConflict());
        inviteByEmail("not-an-email").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void invitesAreOnlyUsableByTheirRecipient() throws Exception {
        inviteByEmail(friend.email()).andExpect(status().isCreated());
        String inviteId = myFirstInviteId(friend);
        TestUser other = newUser();

        mockMvc.perform(as(other, post("/api/invites/" + inviteId + "/accept"))).andExpect(status().isNotFound());
        mockMvc.perform(as(other, post("/api/invites/" + inviteId + "/decline"))).andExpect(status().isNotFound());
        mockMvc.perform(as(other, get("/api/groups/" + groupId))).andExpect(status().isNotFound());
    }

    @Test
    void declineAndCancelRemoveTheInvite() throws Exception {
        inviteByEmail(friend.email()).andExpect(status().isCreated());
        mockMvc.perform(as(friend, post("/api/invites/" + myFirstInviteId(friend) + "/decline")))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(friend, get("/api/invites"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(friend, get("/api/groups/" + groupId))).andExpect(status().isNotFound());

        String inviteId = read(inviteByEmail(friend.email()).andExpect(status().isCreated()).andReturn(), "$.id");
        mockMvc.perform(as(owner, delete("/api/groups/" + groupId + "/invites/" + inviteId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(friend, get("/api/invites"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void inviteCanBeSentBeforeTheRecipientSignsUp() throws Exception {
        String email = "future-" + UUID.randomUUID() + "@example.com";

        // Same response as for a registered email: invites don't reveal who has an account.
        inviteByEmail(email).andExpect(status().isCreated());

        TestUser newcomer = newUser(email);
        mockMvc.perform(as(newcomer, get("/api/invites")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupName").value("Ski trip"));
    }

    @Test
    void joiningByLinkClearsAPendingEmailInvite() throws Exception {
        inviteByEmail(friend.email()).andExpect(status().isCreated());
        linkAction(friend, "accept", createLink()).andExpect(status().isOk());

        mockMvc.perform(as(friend, get("/api/invites"))).andExpect(jsonPath("$.length()").value(0));
    }
}
