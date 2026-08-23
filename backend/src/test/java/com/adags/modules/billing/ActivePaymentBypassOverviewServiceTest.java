package com.adags.modules.billing;

import com.adags.modules.billing.application.ActivePaymentBypassOverviewService;
import com.adags.modules.billing.application.EmergencyBypassRow;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentBypassSource;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 02-billing-architecture.md §5.4: the receptionist's emergency billing
// bar — membership is "active bypass with unsettled balance," and the
// balance is live (summed from outstanding invoices), not a stored figure.
class ActivePaymentBypassOverviewServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ActivePaymentBypassOverviewService activePaymentBypassOverviewService;

    @Autowired
    private PaymentBypassRepository paymentBypassRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "TRUNCATE service_authorization, invoice_line, invoice, payment_bypass, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void listsAnActiveBypassWithItsLiveAccruedBalanceAndPatientIdentity() {
        UUID patientId = insertFixturePatient("Amina", "Hassan");
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        paymentBypassRepository.insert(PaymentBypass.grant(
                encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, "unresponsive", bootstrapAdminId()));
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(87_000, "TZS"), new Money(0, "TZS"), new Money(87_000, "TZS"),
                "TriageCompleted", encounterId));
        invoiceRepository.issue(draft.id(), "FAC-2026-BAR-1", draft.version());

        List<EmergencyBypassRow> overview = activePaymentBypassOverviewService.overview();

        assertThat(overview).hasSize(1);
        EmergencyBypassRow row = overview.get(0);
        assertThat(row.encounterId()).isEqualTo(encounterId);
        assertThat(row.patientName()).isEqualTo("Amina Hassan");
        assertThat(row.outstandingTotalMinor()).isEqualTo(87_000);
    }

    @Test
    void revokedBypassesAreExcluded() {
        UUID patientId = insertFixturePatient("Juma", "Mkapa");
        UUID encounterId = insertFixtureEncounter(patientId);
        PaymentBypass bypass = paymentBypassRepository.insert(PaymentBypass.grant(
                encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, "shock", bootstrapAdminId()));
        jdbcTemplate.update(
                "UPDATE payment_bypass SET status = 'REVOKED', revoked_by = :revokedBy, revoked_at = now() WHERE id = :id",
                new MapSqlParameterSource().addValue("revokedBy", bootstrapAdminId()).addValue("id", bypass.id()));

        List<EmergencyBypassRow> overview = activePaymentBypassOverviewService.overview();

        assertThat(overview).extracting(EmergencyBypassRow::paymentBypassId).doesNotContain(bypass.id());
    }

    private UUID insertFixtureServiceItem() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO service_item (code, name, category)
                        VALUES (:code, 'Test Item', 'CONSULTATION')
                        RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-ITEM-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID insertFixturePatient(String givenName, String familyName) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, :givenName, :familyName, DATE '1990-01-01', 'FEMALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("hospitalNo", "TEST-" + UUID.randomUUID())
                        .addValue("givenName", givenName)
                        .addValue("familyName", familyName),
                UUID.class);
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
                        VALUES (:patientId, :facilityId, :departmentId, 'EMERGENCY', 'IN_CONSULT', 'CASH',
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
