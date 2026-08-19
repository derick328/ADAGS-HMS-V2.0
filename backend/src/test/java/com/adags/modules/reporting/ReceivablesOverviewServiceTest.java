package com.adags.modules.reporting;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.reporting.application.ReceivablesOverview;
import com.adags.modules.reporting.application.ReceivablesOverviewService;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 18-admin.md item 5: confirms the aged-receivables view (item 1)
// distinguishes post-discharge balances from active-encounter
// outstanding — root CLAUDE.md "Billing": "Never block discharge... if
// balance > 0 -> create receivable" — a live encounter's balance is not
// yet chase-worthy the way a post-discharge one is.
class ReceivablesOverviewServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ReceivablesOverviewService receivablesOverviewService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceLineRepository invoiceLineRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE service_authorization, invoice_line, invoice, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void distinguishesPostDischargeFromActiveEncounterOutstanding() {
        issueInvoice(20_000, true);  // post-discharge receivable
        issueInvoice(15_000, false); // still ISSUED — active encounter

        ReceivablesOverview overview = receivablesOverviewService.generate(BOOTSTRAP_FACILITY_ID);

        assertThat(overview.postDischargeOutstandingMinor()).isEqualTo(20_000);
        assertThat(overview.postDischargeInvoiceCount()).isEqualTo(1);
        assertThat(overview.activeEncounterOutstandingMinor()).isEqualTo(15_000);
        assertThat(overview.activeEncounterInvoiceCount()).isEqualTo(1);
    }

    @Test
    void aFullyPaidActiveInvoiceIsNotCountedAsOutstanding() {
        Invoice issued = issueInvoiceRaw(10_000);
        jdbcTemplate.update("UPDATE invoice SET outstanding_total_minor = 0 WHERE id = :id",
                new MapSqlParameterSource("id", issued.id()));

        ReceivablesOverview overview = receivablesOverviewService.generate(BOOTSTRAP_FACILITY_ID);

        assertThat(overview.activeEncounterOutstandingMinor()).isEqualTo(0);
        assertThat(overview.activeEncounterInvoiceCount()).isEqualTo(0);
    }

    private void issueInvoice(long grossMinor, boolean markPostDischarge) {
        Invoice issued = issueInvoiceRaw(grossMinor);
        if (markPostDischarge) {
            invoiceRepository.markReceivablePostDischarge(issued.id());
        }
    }

    private Invoice issueInvoiceRaw(long grossMinor) {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(grossMinor, "TZS"), new Money(0, "TZS"), new Money(grossMinor, "TZS"),
                "TriageCompleted", encounterId));
        return invoiceRepository.issue(draft.id(), "FAC-2026-" + UUID.randomUUID().toString().substring(0, 6), draft.version());
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
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'IN_CONSULT', 'CASH',
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
