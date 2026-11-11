package com.adags.modules.billing;

import com.adags.modules.billing.domain.PaymentBypassSource;
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

// 02-billing-architecture.md §8: the ADMIN_WAIVER route ("charity, staff
// benefit, ministry directive") — an admin grants directly, no second
// actor, unlike CLINICIAN_AUTHORISED (enum value exists, V61, but no
// endpoint yet — needs a Doctor workspace to receive a request).
@AutoConfigureMockMvc
class PaymentBypassControllerTest extends IntegrationTestSupport {

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
        // A failed assertion must never leave the shared Testcontainers admin without its role.
        restoreAdminRole();
        jdbcTemplate.update("TRUNCATE payment_bypass, encounter, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void grantingAnAdminWaiverWithoutThePermissionIsForbidden() throws Exception {
        // Bootstrap admin holds ADMIN by default (V3__BootstrapAdmin), which
        // V61 grants PAYMENT_BYPASS_WAIVE to — so proving the 403 case needs
        // an actor stripped of that role, not the bootstrap admin itself.
        jdbcTemplate.update(
                "DELETE FROM role_assignment WHERE user_id = (SELECT id FROM app_user WHERE username = 'admin')",
                new MapSqlParameterSource());
        String token = login();
        UUID encounterId = insertFixtureEncounter(insertFixturePatient());

        mockMvc.perform(post("/api/payment-bypasses/admin-waiver")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"encounterId": "%s", "reason": "Staff benefit"}
                                """.formatted(encounterId)))
                .andExpect(status().isForbidden());

        restoreAdminRole();
    }

    @Test
    void grantingAnAdminWaiverCreatesAnActiveBypassWithTheAdminWaiverSourceAndSatisfiesTheGate() throws Exception {
        String token = login();
        UUID encounterId = insertFixtureEncounter(insertFixturePatient());

        MvcResult result = mockMvc.perform(post("/api/payment-bypasses/admin-waiver")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"encounterId": "%s", "reason": "Ministry directive — indigent patient"}
                                """.formatted(encounterId)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID bypassId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("paymentBypassId").asText());

        String source = jdbcTemplate.queryForObject(
                "SELECT source FROM payment_bypass WHERE id = :id",
                new MapSqlParameterSource("id", bypassId), String.class);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM payment_bypass WHERE id = :id",
                new MapSqlParameterSource("id", bypassId), String.class);
        assertThat(source).isEqualTo(PaymentBypassSource.ADMIN_WAIVER.name());
        assertThat(status).isEqualTo("ACTIVE");

        // Same encounter-scoped gate clause every other bypass source
        // satisfies — root CLAUDE.md "Billing": "An active PaymentBypass
        // satisfies the gate," regardless of which source granted it.
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_bypass WHERE encounter_id = :id AND status = 'ACTIVE'",
                new MapSqlParameterSource("id", encounterId), Integer.class);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void grantingAnAdminWaiverRequiresANonBlankReason() throws Exception {
        String token = login();
        UUID encounterId = insertFixtureEncounter(insertFixturePatient());

        mockMvc.perform(post("/api/payment-bypasses/admin-waiver")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"encounterId": "%s", "reason": ""}
                                """.formatted(encounterId)))
                .andExpect(status().isBadRequest());
    }

    private void restoreAdminRole() {
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT u.id, r.id, 'GLOBAL', NULL
                        FROM app_user u CROSS JOIN role r
                        WHERE u.username = 'admin' AND r.code = 'ADMIN'
                          AND NOT EXISTS (
                              SELECT 1 FROM role_assignment ra
                              WHERE ra.user_id = u.id AND ra.role_id = r.id AND ra.revoked_at IS NULL
                          )
                        """,
                new MapSqlParameterSource());
    }

    private String login() throws Exception {
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

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertFixtureEncounter(UUID patientId) {
        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);

        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'AWAITING_PAYMENT', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);
    }
}
