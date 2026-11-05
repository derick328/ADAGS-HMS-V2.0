package com.adags.modules.reporting;

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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The HTTP contract for the five revenue analytics endpoints: the
// FINANCIAL_REPORT_READ gate is real on every one of them, anonymous callers
// are rejected before any query runs, and the response shapes the admin
// screens bind to are what actually comes back.
//
// The permission check is the point of the first two tests. UI permission
// checks hide controls; they are not security (frontend/CLAUDE.md), so the
// endpoint has to refuse a caller who simply types the URL.
@AutoConfigureMockMvc
class RevenueAnalyticsApiTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BOOTSTRAP_USERNAME = "admin";
    private static final String BOOTSTRAP_PASSWORD = "Integration-Test-Bootstrap-Pw1";

    private static final String[] ANALYTICS_PATHS = {
            "/api/reporting/revenue/summary",
            "/api/reporting/revenue/by-category",
            "/api/reporting/revenue/trend",
            "/api/reporting/revenue/adjustments",
    };

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /** The low-privilege caller used to prove the permission gate. */
    private static final String RECEPTION_USERNAME = "analytics-test-receptionist";

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        DELETE FROM role_assignment
                        WHERE user_id IN (SELECT id FROM app_user WHERE username = :username)
                        """, new MapSqlParameterSource("username", RECEPTION_USERNAME));
        jdbcTemplate.update("DELETE FROM app_user WHERE username = :username",
                new MapSqlParameterSource("username", RECEPTION_USERNAME));
    }

    @Test
    void everyAnalyticsEndpointRejectsAnAnonymousCaller() throws Exception {
        for (String path : ANALYTICS_PATHS) {
            mockMvc.perform(get(path).param("from", from()).param("to", to())
                            .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/reporting/revenue/top-items")
                        .param("from", from()).param("to", to())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString())
                        .param("category", "PHARMACY"))
                .andExpect(status().isUnauthorized());
    }

    // A role without FINANCIAL_REPORT_READ must be refused even though it
    // authenticates fine — RECEPTIONIST holds PATIENT_SEARCH and billing
    // permissions, but no reporting permission (V5 catalogue).
    @Test
    void everyAnalyticsEndpointRejectsAnAuthenticatedCallerLackingFinancialReportRead() throws Exception {
        String token = loginAsReceptionist();

        for (String path : ANALYTICS_PATHS) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("from", from()).param("to", to())
                            .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/reporting/revenue/top-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from()).param("to", to())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString())
                        .param("category", "PHARMACY"))
                .andExpect(status().isForbidden());
    }

    @Test
    void summaryReturnsTheKpiShapeTheAdminScreenBindsTo() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/reporting/revenue/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from()).param("to", to())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectedCashMinor").isNumber())
                .andExpect(jsonPath("$.grossBilledMinor").isNumber())
                .andExpect(jsonPath("$.patientPortionBilledMinor").isNumber())
                .andExpect(jsonPath("$.insuranceBilledMinor").isNumber())
                .andExpect(jsonPath("$.outstandingTotalMinor").isNumber())
                .andExpect(jsonPath("$.settledInvoiceCount").isNumber())
                .andExpect(jsonPath("$.cohortPatientPortionBilledMinor").isNumber())
                .andExpect(jsonPath("$.currencyCode").value("TZS"));
    }

    // The category list is fixed and complete, radiology included — the
    // chart binds to it positionally and an absent bucket would shift it.
    @Test
    void byCategoryReturnsAllEightCategoriesIncludingRadiology() throws Exception {
        String token = login();

        MvcResult result = mockMvc.perform(get("/api/reporting/revenue/by-category")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from()).param("to", to())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body).hasSize(8);
        assertThat(body.findValuesAsText("category"))
                .containsExactly("CONSULTATION", "SURGERY", "THEATRE_PROCEDURE", "PHARMACY",
                        "LABORATORY", "RADIOLOGY", "INPATIENT", "OTHER");
        assertThat(body.get(5).get("categoryLabel").asText()).isEqualTo("Radiology / imaging");
    }

    @Test
    void adjustmentsReturnsCreditNoteAndWriteOffTotalsSeparately() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/reporting/revenue/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from()).param("to", to())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNotesAppliedMinor").isNumber())
                .andExpect(jsonPath("$.writeOffsAppliedMinor").isNumber())
                .andExpect(jsonPath("$.refundsAppliedMinor").isNumber())
                .andExpect(jsonPath("$.pendingCreditNoteCount").isNumber());
    }

    @Test
    void trendDefaultsItsGranularityFromTheSpanWhenNoneIsGiven() throws Exception {
        String token = login();

        // 3 days -> DAY buckets.
        MvcResult daily = mockMvc.perform(get("/api/reporting/revenue/trend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", LocalDate.now().minusDays(2).toString())
                        .param("to", LocalDate.now().toString())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(daily.getResponse().getContentAsString())).hasSize(3);

        // A year -> MONTH buckets, not 365 points.
        MvcResult monthly = mockMvc.perform(get("/api/reporting/revenue/trend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", LocalDate.now().minusMonths(11).withDayOfMonth(1).toString())
                        .param("to", LocalDate.now().toString())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(monthly.getResponse().getContentAsString())).hasSize(12);
    }

    // An explicit granularity that would blow past the bucket cap is a 400,
    // not a multi-megabyte response.
    @Test
    void anExcessiveDailyTrendIsRejectedWithBadRequest() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/reporting/revenue/trend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", LocalDate.now().minusYears(3).toString())
                        .param("to", LocalDate.now().toString())
                        .param("granularity", "DAY")
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topItemsRejectsAnUnknownCategory() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/reporting/revenue/top-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from()).param("to", to())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString())
                        .param("category", "NOT_A_CATEGORY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topItemsAcceptsRadiologyAsACategory() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/reporting/revenue/top-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", from()).param("to", to())
                        .param("facilityId", BOOTSTRAP_FACILITY_ID.toString())
                        .param("category", "RADIOLOGY"))
                .andExpect(status().isOk());
    }

    private String from() {
        return LocalDate.now().minusDays(1).toString();
    }

    private String to() {
        return LocalDate.now().plusDays(1).toString();
    }

    private String login() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /**
     * A separate RECEPTIONIST-only user rather than stripping the bootstrap
     * admin's roles: role_assignment forbids back-dating valid_to below
     * valid_from, and mutating the shared admin would leak into every other
     * test in the suite-wide container if this one failed part-way.
     *
     * The password hash is copied from the bootstrap admin so the same
     * bootstrap password authenticates — no new credential is introduced
     * and none is written down anywhere.
     */
    private String loginAsReceptionist() throws Exception {
        jdbcTemplate.update("""
                        INSERT INTO app_user (username, password_hash, status)
                        SELECT :username, password_hash, 'ACTIVE' FROM app_user WHERE username = :bootstrap
                        """,
                new MapSqlParameterSource()
                        .addValue("username", RECEPTION_USERNAME)
                        .addValue("bootstrap", BOOTSTRAP_USERNAME));
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT (SELECT id FROM app_user WHERE username = :username), id, 'GLOBAL', NULL
                        FROM role WHERE code = 'RECEPTIONIST'
                        """, new MapSqlParameterSource("username", RECEPTION_USERNAME));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(RECEPTION_USERNAME, BOOTSTRAP_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
