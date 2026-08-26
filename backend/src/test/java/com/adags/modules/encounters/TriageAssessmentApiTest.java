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

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Everything this session's chat added to make Screen 1 (queue) and
// Screen 2 (assessment form) real rather than fabricated: the
// ENCOUNTER_OPEN/TRIAGE_QUEUE_READ dual-permission fix on GET
// /api/encounters, the triage-draft autosave (V60), the referral-candidate
// picker (fee + queue depth), the vital-reference-range read endpoint, and
// the submit controller that finally puts SubmitTriageAssessmentService
// behind HTTP. None of these had a test until now — backend/CLAUDE.md
// "Testing": "every constraint... gets a test that proves it rejects the
// bad case."
@AutoConfigureMockMvc
class TriageAssessmentApiTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BOOTSTRAP_USERNAME = "admin";
    private static final String BOOTSTRAP_PASSWORD = "Integration-Test-Bootstrap-Pw1";
    private static final String FIXTURE_PASSWORD = "Fixture-Password-1";

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
                "DELETE FROM role_assignment WHERE role_id IN (SELECT id FROM role WHERE code IN ('TRIAGE_NURSE', 'DOCTOR', 'RECEPTIONIST'))",
                new MapSqlParameterSource());
        jdbcTemplate.update(
                "DELETE FROM app_user WHERE username IN ('fixture-doctor', 'fixture-second-nurse')",
                new MapSqlParameterSource());
    }

    @Test
    void triageQueueSearchAcceptsTriageQueueReadWithoutEncounterOpen() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();

        // The permission fix: before this session's change, this 403'd for
        // anyone holding only TRIAGE_QUEUE_READ (the controller checked
        // ENCOUNTER_OPEN alone) — the triage nurse's own sidebar count would
        // never have worked.
        mockMvc.perform(get("/api/encounters").param("states", "AWAITING_TRIAGE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void triageQueueSearchStillRejectsNeitherPermission() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/encounters").param("states", "AWAITING_TRIAGE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void triageDraftAutosaveUpsertsByEncounterAndAuthorAndResumesOnGet() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        String encounterId = openEncounterForFreshPatient(token, "MALE");

        mockMvc.perform(put("/api/encounters/" + encounterId + "/triage-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority": "URGENT", "modeOfAmbulation": "WALKING", "formState": {"chiefComplaint": "cough"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.formState.chiefComplaint").value("cough"));

        // Second autosave overwrites the same row (composite PK), not a new one.
        mockMvc.perform(put("/api/encounters/" + encounterId + "/triage-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority": "IMMEDIATE", "modeOfAmbulation": "STRETCHER_ASSISTED", "formState": {"chiefComplaint": "chest pain"}}
                                """))
                .andExpect(status().isOk());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM triage_assessment_draft WHERE encounter_id = :id",
                new MapSqlParameterSource("id", UUID.fromString(encounterId)), Integer.class);
        org.assertj.core.api.Assertions.assertThat(rowCount).isEqualTo(1);

        mockMvc.perform(get("/api/encounters/" + encounterId + "/triage-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("IMMEDIATE"))
                .andExpect(jsonPath("$.modeOfAmbulation").value("STRETCHER_ASSISTED"));
    }

    @Test
    void activeSummariesSurfacesOnlyTheLatestDraftAcrossAuthors() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String adminToken = login();
        String encounterId = openEncounterForFreshPatient(adminToken, "MALE");

        UUID secondNurseId = insertFixtureUser("fixture-second-nurse");
        grantRole(secondNurseId, "TRIAGE_NURSE");

        // admin drafts first...
        mockMvc.perform(put("/api/encounters/" + encounterId + "/triage-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority": "NON_URGENT", "formState": {}}
                                """))
                .andExpect(status().isOk());

        // ...then a second nurse also opens the same patient (06-triage.md
        // §3: "a second nurse CAN open the same patient" — no lock).
        String secondToken = loginAs("fixture-second-nurse");
        mockMvc.perform(put("/api/encounters/" + encounterId + "/triage-draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority": "URGENT", "formState": {}}
                                """))
                .andExpect(status().isOk());

        // The queue projection shows only the most-recently-updated draft —
        // the second nurse's, not both.
        mockMvc.perform(get("/api/triage-assessment-drafts/active-summaries")
                        .param("encounterIds", encounterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorUsername").value("fixture-second-nurse"))
                .andExpect(jsonPath("$[0].priority").value("URGENT"));

        // Cleanup order matters: the draft row FKs to app_user, and only
        // @AfterEach's TRUNCATE encounter CASCADE clears triage_assessment_draft —
        // deleting app_user here, before that runs, would violate the FK.
        // Leaving the row for @AfterEach's named cleanup instead.
    }

    @Test
    void vitalReferenceRangesReturnsSeededAdultBoundsForAnAdultPatient() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        String patientId = registerFreshPatient(token, "MALE");

        mockMvc.perform(get("/api/clinical/vital-reference-ranges")
                        .param("patientId", patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.vitalType == 'TEMPERATURE')].implausibleLow").value(org.hamcrest.Matchers.hasItem(30.0)))
                .andExpect(jsonPath("$[?(@.vitalType == 'TEMPERATURE')].abnormalHigh").value(org.hamcrest.Matchers.hasItem(38.0)))
                .andExpect(jsonPath("$[?(@.vitalType == 'SPO2')].abnormalLow").value(org.hamcrest.Matchers.hasItem(94.0)));
    }

    @Test
    void referralCandidatesResolvesRealFeeAndQueueDepthForAnActiveDoctor() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        String encounterId = openEncounterForFreshPatient(token, "MALE");

        UUID doctorId = insertFixtureUser("fixture-doctor");
        grantRole(doctorId, "DOCTOR");

        mockMvc.perform(get("/api/encounters/" + encounterId + "/referral-candidates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.providerId == '" + doctorId + "')].roleLabel").value(org.hamcrest.Matchers.hasItem("General")))
                .andExpect(jsonPath("$[?(@.providerId == '" + doctorId + "')].feeAmountMinor").value(org.hamcrest.Matchers.hasItem(10000)))
                .andExpect(jsonPath("$[?(@.providerId == '" + doctorId + "')].currencyCode").value(org.hamcrest.Matchers.hasItem("TZS")))
                .andExpect(jsonPath("$[?(@.providerId == '" + doctorId + "')].queueDepth").value(org.hamcrest.Matchers.hasItem(0)));
    }

    @Test
    void submitTriageAssessmentEndToEndPostsChargeAndTransitionsToAwaitingPayment() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        String encounterId = openEncounterForFreshPatient(token, "MALE");
        UUID doctorId = insertFixtureUser("fixture-doctor");
        grantRole(doctorId, "DOCTOR");

        MvcResult result = mockMvc.perform(post("/api/encounters/" + encounterId + "/triage-assessments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitBody(doctorId, "URGENT", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.get("id").asText()).isNotBlank();

        // §6 step 7: URGENT follows the normal gate — AWAITING_PAYMENT, not bypassed.
        mockMvc.perform(get("/api/encounters/" + encounterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.assignedProviderId").value(doctorId.toString()));

        // §6 step 6: the consultation charge, at the real seeded CASH price
        // for a DOCTOR-role provider (V15 + V66: CONSULT_GENERAL = 10,000).
        Long grossMinor = jdbcTemplate.queryForObject("""
                        SELECT il.gross_minor FROM invoice_line il JOIN invoice i ON i.id = il.invoice_id
                        WHERE i.encounter_id = :id AND il.source_event_type = 'TriageCompleted'
                        """,
                new MapSqlParameterSource("id", UUID.fromString(encounterId)), Long.class);
        org.assertj.core.api.Assertions.assertThat(grossMinor).isEqualTo(10_000L);
    }

    @Test
    void submitWithImmediatePriorityCreatesBypassAndSkipsStraightToAwaitingConsult() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        String encounterId = openEncounterForFreshPatient(token, "MALE");
        UUID doctorId = insertFixtureUser("fixture-doctor");
        grantRole(doctorId, "DOCTOR");

        mockMvc.perform(post("/api/encounters/" + encounterId + "/triage-assessments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitBody(doctorId, "IMMEDIATE", "ACTIVE_CONVULSION", null)))
                .andExpect(status().isCreated());

        // root CLAUDE.md "Safety": "IMMEDIATE triage priority bypasses the
        // payment gate for the whole encounter" — AWAITING_PAYMENT skipped
        // entirely, straight to AWAITING_CONSULT.
        mockMvc.perform(get("/api/encounters/" + encounterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_CONSULT"))
                .andExpect(jsonPath("$.type").value("EMERGENCY"));

        Integer bypassCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_bypass WHERE encounter_id = :id AND status = 'ACTIVE'",
                new MapSqlParameterSource("id", UUID.fromString(encounterId)), Integer.class);
        org.assertj.core.api.Assertions.assertThat(bypassCount).isEqualTo(1);
    }

    @Test
    void submitWithAnImplausibleVitalIsRejectedNamingTheBoundAndSavesNothing() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        String encounterId = openEncounterForFreshPatient(token, "MALE");
        UUID doctorId = insertFixtureUser("fixture-doctor");
        grantRole(doctorId, "DOCTOR");

        // 44 is above the seeded ADULT implausible_high (43) for temperature.
        mockMvc.perform(post("/api/encounters/" + encounterId + "/triage-assessments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitBody(doctorId, "URGENT", null, 44.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("43")));

        // Nothing partially saved — §6: a throw during the transaction
        // rolls back everything, the encounter is still AWAITING_TRIAGE.
        mockMvc.perform(get("/api/encounters/" + encounterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_TRIAGE"));
    }

    @Test
    void completedAssessmentsRequiresTriageHistoryReadPermission() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/triage-assessments")
                        .param("assessedFrom", "2000-01-01T00:00:00Z")
                        .param("assessedTo", "2999-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void completedAssessmentsOwnOnlyFiltersToTheCallingNurseNotEveryAuthor() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String adminToken = login();
        UUID doctorId = insertFixtureUser("fixture-doctor");
        grantRole(doctorId, "DOCTOR");

        String encounterA = openEncounterForFreshPatient(adminToken, "MALE");
        mockMvc.perform(post("/api/encounters/" + encounterA + "/triage-assessments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitBody(doctorId, "URGENT", null, null)))
                .andExpect(status().isCreated());

        UUID secondNurseId = insertFixtureUser("fixture-second-nurse");
        grantRole(secondNurseId, "TRIAGE_NURSE");
        String secondToken = loginAs("fixture-second-nurse");
        String encounterB = openEncounterForFreshPatient(adminToken, "MALE");
        mockMvc.perform(post("/api/encounters/" + encounterB + "/triage-assessments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitBody(doctorId, "URGENT", null, null)))
                .andExpect(status().isCreated());

        // §7.12 resolved: ownOnly=true is scoped server-side to the caller's
        // own userId — admin cannot see the second nurse's row this way.
        mockMvc.perform(get("/api/triage-assessments")
                        .param("ownOnly", "true")
                        .param("assessedFrom", "2000-01-01T00:00:00Z")
                        .param("assessedTo", "2999-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].encounterId").value(encounterA))
                .andExpect(jsonPath("$[0].assessedByUsername").value(BOOTSTRAP_USERNAME));

        // Omitting ownOnly is "the whole shift" — every author's rows.
        mockMvc.perform(get("/api/triage-assessments")
                        .param("assessedFrom", "2000-01-01T00:00:00Z")
                        .param("assessedTo", "2999-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void completedAssessmentsSearchIsBoundedByTheDateRange() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        UUID doctorId = insertFixtureUser("fixture-doctor");
        grantRole(doctorId, "DOCTOR");
        String encounterId = openEncounterForFreshPatient(token, "MALE");

        Instant beforeSubmission = Instant.now();
        mockMvc.perform(post("/api/encounters/" + encounterId + "/triage-assessments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitBody(doctorId, "URGENT", null, null)))
                .andExpect(status().isCreated());
        Instant afterSubmission = Instant.now().plusSeconds(5);

        mockMvc.perform(get("/api/triage-assessments")
                        .param("assessedFrom", "2000-01-01T00:00:00Z")
                        .param("assessedTo", beforeSubmission.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/triage-assessments")
                        .param("assessedFrom", beforeSubmission.toString())
                        .param("assessedTo", afterSubmission.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].encounterId").value(encounterId));
    }

    @Test
    void completedAssessmentsExcludesARowThatHasBeenSuperseded() throws Exception {
        UUID adminId = bootstrapAdminId();
        grantRole(adminId, "TRIAGE_NURSE");
        String token = login();
        UUID doctorId = insertFixtureUser("fixture-doctor");
        grantRole(doctorId, "DOCTOR");
        String encounterId = openEncounterForFreshPatient(token, "MALE");

        MvcResult result = mockMvc.perform(post("/api/encounters/" + encounterId + "/triage-assessments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSubmitBody(doctorId, "URGENT", null, null)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID originalId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        // No amendment endpoint exists yet (Screen 3 is read-only, no
        // correction flow built this pass) — simulating the row shape a
        // future correction would produce directly, to prove the read
        // side already honours it: an "amendment" row whose supersedes_id
        // points at the original.
        UUID amendmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO triage_assessment (encounter_id, chief_complaint, current_symptoms, allergies,
                            comorbidities, mode_of_ambulation, has_pain, fall_risk, infection_risk, priority,
                            supersedes_id, assessed_by)
                        VALUES (:encounterId, 'Corrected complaint', 'symptoms', 'none known', 'none known',
                            'WALKING', false, false, false, 'URGENT', :originalId, :assessedBy)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", UUID.fromString(encounterId))
                        .addValue("originalId", originalId)
                        .addValue("assessedBy", adminId),
                UUID.class);

        mockMvc.perform(get("/api/triage-assessments")
                        .param("assessedFrom", "2000-01-01T00:00:00Z")
                        .param("assessedTo", "2999-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(amendmentId.toString()));
    }

    private String validSubmitBody(UUID doctorId, String priority, String immediateReason, Double temperatureOverride) {
        double temperature = temperatureOverride != null ? temperatureOverride : 37.0;
        String immediateReasonJson = immediateReason != null ? "\"" + immediateReason + "\"" : "null";
        return """
                {
                  "expectedEncounterVersion": 0,
                  "assignedProviderId": "%s",
                  "chiefComplaint": "Fever and cough",
                  "currentSymptoms": "Fever since yesterday",
                  "allergies": "none known",
                  "comorbidities": "none known",
                  "modeOfAmbulation": "WALKING",
                  "hasPain": false,
                  "fallRisk": false,
                  "infectionRisk": false,
                  "priority": "%s",
                  "immediateReason": %s,
                  "temperatureC": %s,
                  "systolicMmHg": 120,
                  "diastolicMmHg": 80,
                  "pulseBpm": 75,
                  "respiratoryRate": 16,
                  "spo2Pct": 98,
                  "weightKg": 70,
                  "heightCm": 170
                }
                """.formatted(doctorId, priority, immediateReasonJson, temperature);
    }

    private String openEncounterForFreshPatient(String token, String gender) throws Exception {
        String patientId = registerFreshPatient(token, gender);
        UUID departmentId = insertFixtureDepartment();
        MvcResult openResult = mockMvc.perform(post("/api/encounters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "departmentId": "%s"}
                                """.formatted(patientId, departmentId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(openResult.getResponse().getContentAsString()).get("id").asText();
    }

    // TRIAGE_NURSE holds neither PATIENT_CREATE nor ENCOUNTER_OPEN (only
    // reception does) — registering/checking in a fixture patient for a
    // test needs RECEPTIONIST too, granted here so every call site doesn't
    // have to remember it. The actual permission boundary under test is
    // whatever the test itself asserts on afterward, not this setup step.
    private String registerFreshPatient(String token, String gender) throws Exception {
        grantRole(bootstrapAdminId(), "RECEPTIONIST");
        MvcResult registerResult = mockMvc.perform(post("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "givenName": "Test",
                                  "familyName": "Patient-%s",
                                  "dateOfBirth": "1990-01-01",
                                  "gender": "%s",
                                  "phone": "+2557%08d",
                                  "nextOfKin": {"fullName": "Next Kin", "relationship": "SIBLING", "phone": "+255711000000", "isGuardian": false},
                                  "facilityId": "%s"
                                }
                                """.formatted(UUID.randomUUID().toString().substring(0, 6),
                                gender, Math.abs(UUID.randomUUID().hashCode() % 100_000_000), BOOTSTRAP_FACILITY_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("id").asText();
    }

    private String login() throws Exception {
        return loginAs(BOOTSTRAP_USERNAME);
    }

    private String loginAs(String username) throws Exception {
        String password = username.equals(BOOTSTRAP_USERNAME) ? BOOTSTRAP_PASSWORD : FIXTURE_PASSWORD;
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void grantRole(UUID userId, String roleCode) {
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = :roleCode
                        """,
                new MapSqlParameterSource().addValue("userId", userId).addValue("roleCode", roleCode));
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

    // Through the real POST /api/users (CreateUserService) as the bootstrap
    // admin — fixture users log in through the real /auth/login flow like
    // any other actor, never a raw password_hash guessed into existence.
    private UUID insertFixtureUser(String username) throws Exception {
        String adminToken = login();
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "temporaryPassword": "%s"}
                                """.formatted(username, FIXTURE_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
