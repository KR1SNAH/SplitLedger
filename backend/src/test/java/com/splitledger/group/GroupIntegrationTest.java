package com.splitledger.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.splitledger.IntegrationTest;

class GroupIntegrationTest extends IntegrationTest {

    private TestUser owner;
    private TestUser member;
    private TestUser outsider;
    private String groupId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newUser();
        member = newUser();
        outsider = newUser();
        groupId = createGroup(owner, "Lisbon trip");
        addMember(owner, groupId, member);
    }

    private MockHttpServletRequestBuilder changeRole(TestUser actor, TestUser target, String role) {
        return withJson(put("/api/groups/" + groupId + "/members/" + target.id() + "/role"), actor.token(), """
                {"role": "%s"}
                """.formatted(role));
    }

    @Test
    void creatorBecomesOwnerAndGroupUsesTheirCurrency() throws Exception {
        TestUser user = newUser();
        mockMvc.perform(withJson(post("/api/groups"), user.token(), """
                        {"name": "  Flat 4B  ", "description": "Rent and bills"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Flat 4B"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(user.id()))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void listShowsMyGroupsWithRoleAndMemberCount() throws Exception {
        createGroup(owner, "Another group");

        mockMvc.perform(as(owner, get("/api/groups")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Another group"))
                .andExpect(jsonPath("$[0].memberCount").value(1))
                .andExpect(jsonPath("$[1].name").value("Lisbon trip"))
                .andExpect(jsonPath("$[1].memberCount").value(2))
                .andExpect(jsonPath("$[1].myRole").value("OWNER"));

        mockMvc.perform(as(member, get("/api/groups")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].myRole").value("MEMBER"));

        mockMvc.perform(as(outsider, get("/api/groups"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validatesGroups() throws Exception {
        mockMvc.perform(withJson(post("/api/groups"), owner.token(), """
                        {"name": " "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        mockMvc.perform(withJson(post("/api/groups"), owner.token(), """
                        {"name": "Trip", "currency": "usd"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
        mockMvc.perform(withJson(post("/api/groups"), owner.token(), """
                        {"name": "Trip", "currency": "XYZ"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonMembersCannotTellTheGroupExists() throws Exception {
        String path = "/api/groups/" + groupId;
        mockMvc.perform(as(outsider, get(path))).andExpect(status().isNotFound());
        mockMvc.perform(withJson(put(path), outsider.token(), """
                        {"name": "Hijacked"}
                        """))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, delete(path))).andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, post(path + "/leave"))).andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, delete(path + "/members/" + member.id()))).andExpect(status().isNotFound());
        mockMvc.perform(changeRole(outsider, outsider, "OWNER")).andExpect(status().isNotFound());

        // Same answer as for a group that really doesn't exist.
        mockMvc.perform(as(outsider, get("/api/groups/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void membersCanViewButNotManage() throws Exception {
        String path = "/api/groups/" + groupId;
        mockMvc.perform(as(member, get(path)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));

        mockMvc.perform(withJson(put(path), member.token(), """
                        {"name": "Renamed"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").exists());
        mockMvc.perform(as(member, delete(path))).andExpect(status().isForbidden());
        mockMvc.perform(as(member, delete(path + "/members/" + owner.id()))).andExpect(status().isForbidden());
        mockMvc.perform(changeRole(member, member, "OWNER")).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanUpdateAndDelete() throws Exception {
        String path = "/api/groups/" + groupId;
        mockMvc.perform(withJson(put(path), owner.token(), """
                        {"name": "Lisbon 2027", "description": "", "currency": "EUR"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lisbon 2027"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.currency").value("EUR"));

        // Omitting the currency keeps it.
        mockMvc.perform(withJson(put(path), owner.token(), """
                        {"name": "Lisbon"}
                        """))
                .andExpect(jsonPath("$.currency").value("EUR"));

        mockMvc.perform(as(owner, delete(path))).andExpect(status().isNoContent());
        mockMvc.perform(as(owner, get(path))).andExpect(status().isNotFound());
        mockMvc.perform(as(member, get("/api/groups"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void memberCanLeave() throws Exception {
        mockMvc.perform(as(member, post("/api/groups/" + groupId + "/leave"))).andExpect(status().isNoContent());
        mockMvc.perform(as(member, get("/api/groups/" + groupId))).andExpect(status().isNotFound());
        mockMvc.perform(as(owner, get("/api/groups/" + groupId))).andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void lastOwnerCannotLeaveOrStepDown() throws Exception {
        mockMvc.perform(as(owner, post("/api/groups/" + groupId + "/leave"))).andExpect(status().isConflict());
        mockMvc.perform(changeRole(owner, owner, "MEMBER")).andExpect(status().isConflict());

        // Once someone else is an owner, the original owner can step down or leave.
        mockMvc.perform(changeRole(owner, member, "OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
        mockMvc.perform(as(owner, post("/api/groups/" + groupId + "/leave"))).andExpect(status().isNoContent());

        mockMvc.perform(as(member, get("/api/groups/" + groupId)))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void ownersCanDemoteEachOther() throws Exception {
        mockMvc.perform(changeRole(owner, member, "OWNER")).andExpect(status().isOk());
        mockMvc.perform(changeRole(member, owner, "MEMBER")).andExpect(status().isOk());

        // The former owner has lost owner rights.
        mockMvc.perform(changeRole(owner, owner, "OWNER")).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanRemoveMembersButNotThemselves() throws Exception {
        String members = "/api/groups/" + groupId + "/members/";
        mockMvc.perform(as(owner, delete(members + owner.id()))).andExpect(status().isBadRequest());
        mockMvc.perform(as(owner, delete(members + outsider.id()))).andExpect(status().isNotFound());

        mockMvc.perform(as(owner, delete(members + member.id()))).andExpect(status().isNoContent());
        mockMvc.perform(as(member, get("/api/groups/" + groupId))).andExpect(status().isNotFound());
    }

    /**
     * Two owners step down at the same moment. Without the row lock both could see "2 owners" and
     * succeed, leaving the group with none. With it, exactly one wins.
     */
    @Test
    void concurrentStepDownsKeepAnOwner() throws Exception {
        mockMvc.perform(changeRole(owner, member, "OWNER")).andExpect(status().isOk());

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> stepDowns = List.of(owner, member).stream()
                .<Callable<Integer>>map(user -> () -> {
                    start.await();
                    return mockMvc.perform(changeRole(user, user, "MEMBER")).andReturn().getResponse().getStatus();
                })
                .toList();

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = stepDowns.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        List<String> roles = read(mockMvc.perform(as(owner, get("/api/groups/" + groupId))).andReturn(), "$.members[*].role");
        assertThat(roles).containsExactlyInAnyOrder("OWNER", "MEMBER");
    }
}
