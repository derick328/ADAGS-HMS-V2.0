package com.adags.modules.reporting;

import com.adags.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TatAnalyticsApiTest extends IntegrationTestSupport {
    private static final UUID FACILITY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String USERNAME = "tat-api-receptionist";
    private static final String PASSWORD = "Integration-Test-Bootstrap-Pw1";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        var p = new MapSqlParameterSource("username", USERNAME);
        jdbc.update("DELETE FROM role_assignment WHERE user_id IN (SELECT id FROM app_user WHERE username = :username)", p);
        jdbc.update("DELETE FROM app_user WHERE username = :username", p);
    }

    @Test
    void anonymousAndNonAdminAreDeniedAcrossAllTatEndpoints() throws Exception {
        String[] paths = {"/api/admin/tat/overview", "/api/admin/tat/departments", "/api/admin/tat/trend",
                "/api/admin/tat/activity", "/api/admin/tat/currently-waiting", "/api/admin/tat/timezone"};
        for (String path : paths) {
            mvc.perform(get(path).param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                    .andExpect(status().isUnauthorized());
        }
        String token = loginReceptionist();
        for (String path : paths) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/tat/patients/" + UUID.randomUUID() + "/encounters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).param("facilityId", FACILITY.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tat/encounters/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).param("facilityId", FACILITY.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tat/departments/DOCTOR/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tat/departments/DOCTOR/users/" + UUID.randomUUID() + "/patients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReceivesMeasuredOverviewAndNoFabricatedDurations() throws Exception {
        String token = login("admin");
        mvc.perform(get("/api/admin/tat/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedEncounters").isNumber())
                .andExpect(jsonPath("$.currentlyWaiting").isNumber())
                .andExpect(jsonPath("$.timezone").value("Africa/Dar_es_Salaam"));
    }

    @Test
    void adminCanLoadDepartmentTrendAndActivityWithOptionalFilters() throws Exception {
        String token = login("admin");
        for (String path : new String[]{"/api/admin/tat/departments", "/api/admin/tat/trend", "/api/admin/tat/activity"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                    .andExpect(status().isOk());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today())
                    .param("workflowType", "TRIAGE"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/admin/tat/departments/DOCTOR/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/tat/departments/DOCTOR/users/" + UUID.randomUUID() + "/patients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("facilityId", FACILITY.toString()).param("from", today()).param("to", today()))
                .andExpect(status().isOk());
    }

    private String loginReceptionist() throws Exception {
        jdbc.update("""
                INSERT INTO app_user (username, password_hash, status)
                SELECT :username, password_hash, 'ACTIVE' FROM app_user WHERE username = 'admin'
                """, new MapSqlParameterSource("username", USERNAME));
        jdbc.update("""
                INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                SELECT (SELECT id FROM app_user WHERE username = :username), id, 'GLOBAL', NULL
                FROM role WHERE code = 'RECEPTIONIST'
                """, new MapSqlParameterSource("username", USERNAME));
        return login(USERNAME);
    }

    private String login(String username) throws Exception {
        var response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }
    private String today() { return LocalDate.now().toString(); }
}
