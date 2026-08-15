package com.adags.modules.identity;

import com.adags.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// The bootstrap admin (V3__BootstrapAdmin) is the one way in before any role
// has been hand-assigned — this walks its full login -> me -> refresh -> logout cycle.
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends IntegrationTestSupport {

    private static final String BOOTSTRAP_USERNAME = "admin";
    private static final String BOOTSTRAP_PASSWORD = "Integration-Test-Bootstrap-Pw1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(BOOTSTRAP_USERNAME, "not-the-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void fullLoginMeRefreshLogoutCycle() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();
        String refreshCookie = extractCookieValue(loginResult, "adags_refresh");

        MvcResult meResult = mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(BOOTSTRAP_USERNAME))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();

        JsonNode meJson = objectMapper.readTree(meResult.getResponse().getContentAsString());
        assertThat(meJson.get("permissions")).anyMatch(node -> node.asText().equals("USER_MANAGE"));
        assertThat(meJson.get("scopes")).anyMatch(node -> node.get("scopeType").asText().equals("GLOBAL"));

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.COOKIE, "adags_refresh=" + refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccessToken = refreshJson.get("accessToken").asText();
        assertThat(newAccessToken).isNotEqualTo(accessToken);

        // The rotated-out refresh token must not work a second time.
        mockMvc.perform(post("/auth/refresh").header(HttpHeaders.COOKIE, "adags_refresh=" + refreshCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isUnauthorized());
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginRequestFixture(username, password));
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).as("Set-Cookie header").isNotNull();
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                return trimmed.substring((cookieName + "=").length());
            }
        }
        throw new IllegalStateException("Cookie " + cookieName + " not found in Set-Cookie header: " + header);
    }

    private record LoginRequestFixture(String username, String password) {
    }
}
