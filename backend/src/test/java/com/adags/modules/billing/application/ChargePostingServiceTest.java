package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §4: "Every charge is posted by a domain
// event." Exercised end to end against the real V15-seeded CASH prices —
// no manual charge-entry path exists to test instead.
class ChargePostingServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ChargePostingService service;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "TRUNCATE service_authorization, invoice_line, invoice, outbox_event, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void postsAChargeAgainstTheSeededCashPriceAndIssuesTheInvoice() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID triageAssessmentId = UUID.randomUUID();

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TriageCompleted", triageAssessmentId, bootstrapAdminId()));

        assertThat(result.grossAmount()).isEqualTo(new Money(1_000_000, "TZS"));
        assertThat(result.patientPortion()).isEqualTo(new Money(1_000_000, "TZS"));
        assertThat(result.invoiceNumber()).isNotBlank();

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM invoice WHERE id = :id",
                new MapSqlParameterSource("id", result.invoiceId()), String.class);
        assertThat(state).isEqualTo("ISSUED");

        Integer chargePostedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'ChargePosted' AND payload->>'invoiceId' = :id",
                new MapSqlParameterSource("id", result.invoiceId().toString()), Integer.class);
        assertThat(chargePostedCount).isEqualTo(1);

        Integer invoiceIssuedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'InvoiceIssued' AND payload->>'invoiceId' = :id",
                new MapSqlParameterSource("id", result.invoiceId().toString()), Integer.class);
        assertThat(invoiceIssuedCount).isEqualTo(1);
    }

    @Test
    void quantityMultipliesTheResolvedUnitPrice() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_SPECIALIST", 2, "TriageCompleted", UUID.randomUUID(), bootstrapAdminId()));

        assertThat(result.grossAmount()).isEqualTo(new Money(8_000_000, "TZS"));
    }

    // root CLAUDE.md "Billing": "A missing price... blocks with an
    // explicit error — it never defaults to zero," and §4.1's "⚠ The
    // charge posting is inside the triage submission transaction. If it
    // fails, the whole assessment rolls back" — proven here by asserting
    // no invoice row survives the failed post.
    @Test
    void anUnconfiguredPayerCategoryBlocksAndPostsNoInvoice() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);

        assertThatThrownBy(() -> service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                "CONSULT_GENERAL", 1, "TriageCompleted", UUID.randomUUID(), bootstrapAdminId())))
                .isInstanceOf(PriceNotConfiguredException.class);

        Integer invoiceCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(invoiceCount).isZero();
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
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
