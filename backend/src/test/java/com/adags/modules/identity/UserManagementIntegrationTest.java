package com.adags.modules.identity;

import com.adags.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 18-admin.md §4: user accounts and scoped role assignments — the "Users"
// admin screen's backend. Covers the two invariants that matter most here:
// duplicate usernames are rejected (UNIQUE app_user.username) and nobody,
// admin included, can grant themselves a role (§11, V57's DB CHECK plus its
// application-layer twin).
@AutoConfigureMockMvc
class UserManagementIntegrationTest extends IntegrationTestSupport {

    private static final String BOOTSTRAP_USERNAME = "admin";
    private static final String BOOTSTRAP_PASSWORD = "Integration-Test-Bootstrap-Pw1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUserThenDuplicateUsernameIsRejected() throws Exception {
        String adminToken = loginAsBootstrapAdmin();
        String username = "nurse_" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "temporaryPassword", "Temp-Password-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "temporaryPassword", "Another-Password-1"))))
                .andExpect(status().isConflict());
    }

    @Test
    void listedUserIncludesNewlyCreatedAccount() throws Exception {
        String adminToken = loginAsBootstrapAdmin();
        String username = "nurse_" + UUID.randomUUID().toString().substring(0, 8);
        createUser(adminToken, username, "Temp-Password-1");

        MvcResult result = mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode users = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(users).anyMatch(node -> node.get("username").asText().equals(username));
    }

    @Test
    void grantingARoleToAnotherUserSucceedsAndCanBeEnded() throws Exception {
        String adminToken = loginAsBootstrapAdmin();
        String username = "nurse_" + UUID.randomUUID().toString().substring(0, 8);
        UUID nurseId = createUser(adminToken, username, "Temp-Password-1");

        MvcResult grantResult = mockMvc.perform(post("/api/users/" + nurseId + "/role-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleCode", "NURSE", "scopeType", "GLOBAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleCode").value("NURSE"))
                .andExpect(jsonPath("$.revokedAt").doesNotExist())
                .andReturn();

        JsonNode grant = objectMapper.readTree(grantResult.getResponse().getContentAsString());
        String assignmentId = grant.get("id").asText();

        mockMvc.perform(get("/api/users/" + nurseId + "/role-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleCode").value("NURSE"));

        mockMvc.perform(post("/api/users/" + nurseId + "/role-assignments/" + assignmentId + "/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + nurseId + "/role-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revokedAt").exists());

        // Ending an already-ended assignment is rejected, not a silent no-op.
        mockMvc.perform(post("/api/users/" + nurseId + "/role-assignments/" + assignmentId + "/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // 18-admin.md §11: "Admin cannot silently elevate themselves." The
    // bootstrap admin granting a role to their own account must fail —
    // this is the floor under the fuller second-admin-approval workflow.
    @Test
    void adminCannotGrantARoleToThemselves() throws Exception {
        String adminToken = loginAsBootstrapAdmin();
        UUID adminId = currentUserId(adminToken);

        mockMvc.perform(post("/api/users/" + adminId + "/role-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleCode", "ACCOUNTANT", "scopeType", "GLOBAL"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleCatalogueListsSeededRoles() throws Exception {
        String adminToken = loginAsBootstrapAdmin();

        MvcResult result = mockMvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode roles = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(roles).anyMatch(node -> node.get("code").asText().equals("NURSE"));
        assertThat(roles).anyMatch(node -> node.get("code").asText().equals("ADMIN"));
    }

    @Test
    void changingOwnPasswordWithWrongCurrentPasswordIsRejected() throws Exception {
        String adminToken = loginAsBootstrapAdmin();

        mockMvc.perform(post("/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "not-the-password", "newPassword", "New-Password-123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingOwnPasswordClearsMustChangePasswordAndTakesEffect() throws Exception {
        String username = "pwchange_" + UUID.randomUUID().toString().substring(0, 8);
        String adminToken = loginAsBootstrapAdmin();
        createUser(adminToken, username, "Original-Password-1");
        String userToken = login(username, "Original-Password-1");

        mockMvc.perform(post("/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Original-Password-1", "newPassword", "Brand-New-Password-1"))))
                .andExpect(status().isNoContent());

        // The old password no longer works...
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "Original-Password-1"))))
                .andExpect(status().isUnauthorized());

        // ...and the new one does, with mustChangePassword now cleared.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "Brand-New-Password-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    private UUID createUser(String adminToken, String username, String temporaryPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "temporaryPassword", temporaryPassword))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID currentUserId(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("userId").asText());
    }

    private String loginAsBootstrapAdmin() throws Exception {
        return login(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
