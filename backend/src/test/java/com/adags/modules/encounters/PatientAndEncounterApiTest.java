package com.adags.modules.encounters;

import com.adags.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 05-receptionist.md §8: REST surface for reception's own slice — register,
// search, check-in, dismiss. Exercises AuthorizationService wired into a
// controller for the first time (root CLAUDE.md "Authorisation": "Check
// permissions, never role names").
@AutoConfigureMockMvc
class PatientAndEncounterApiTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BOOTSTRAP_USERNAME = "admin";
    private static final String BOOTSTRAP_PASSWORD = "Integration-Test-Bootstrap-Pw1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE encounter, patient CASCADE", new MapSqlParameterSource());
        jdbcTemplate.update(
                "DELETE FROM role_assignment WHERE role_id = (SELECT id FROM role WHERE code = 'RECEPTIONIST')",
                new MapSqlParameterSource());
    }

    @Test
    void registeringAPatientWithoutThePermissionIsForbidden() throws Exception {
        String token = loginAsBootstrapAdmin();

        mockMvc.perform(post("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPatientBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerSearchCheckInAndDismissThroughTheRealEndpoints() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantReceptionist(adminId);
        String token = loginAsBootstrapAdmin();

        MvcResult registerResult = mockMvc.perform(post("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPatientBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Amina Hassan"))
                .andReturn();
        JsonNode registered = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String patientId = registered.get("id").asText();

        mockMvc.perform(get("/api/patients/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("q", "Amina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Amina Hassan"));

        mockMvc.perform(get("/api/patients/" + patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalNo").isNotEmpty());

        UUID departmentId = insertFixtureDepartment();
        MvcResult openResult = mockMvc.perform(post("/api/encounters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "departmentId": "%s"}
                                """.formatted(patientId, departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("AWAITING_TRIAGE"))
                .andReturn();
        JsonNode opened = objectMapper.readTree(openResult.getResponse().getContentAsString());
        String encounterId = opened.get("id").asText();

        // ⚠7: a second check-in for the same patient returns the existing
        // encounter rather than erroring or creating a duplicate.
        mockMvc.perform(post("/api/encounters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "departmentId": "%s"}
                                """.formatted(patientId, departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(encounterId));

        mockMvc.perform(get("/api/encounters/" + encounterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_TRIAGE"));

        mockMvc.perform(post("/api/encounters/" + encounterId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outcome": "LEFT_WITHOUT_BEING_SEEN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LEFT_WITHOUT_BEING_SEEN"));

        // ⚠8: dismissing the encounter must never deactivate the patient.
        mockMvc.perform(get("/api/patients/" + patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    private String registerPatientBody() {
        return """
                {
                  "givenName": "Amina",
                  "familyName": "Hassan",
                  "dateOfBirth": "1990-01-01",
                  "gender": "FEMALE",
                  "phone": "+255700000001",
                  "nextOfKin": {"fullName": "Next Kin", "relationship": "SIBLING", "phone": "+255711000000", "isGuardian": false},
                  "facilityId": "%s"
                }
                """.formatted(BOOTSTRAP_FACILITY_ID);
    }

    private String loginAsBootstrapAdmin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return loginJson.get("accessToken").asText();
    }

    private void grantReceptionist(UUID userId) {
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'RECEPTIONIST'
                        """,
                new MapSqlParameterSource("userId", userId));
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
