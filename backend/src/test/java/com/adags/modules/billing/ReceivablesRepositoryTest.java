package com.adags.modules.billing;

import com.adags.modules.billing.domain.AgingBucket;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ReceivableInvoice;
import com.adags.modules.billing.domain.ReceivableSummary;
import com.adags.modules.billing.domain.ReceivablesRepository;
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

// 18-admin.md (Phase 7 item 1): "Aged receivables view: current, 30, 60,
// 90+, by payer category." receivable_at is backdated directly via SQL
// here — the repository has no "as of" parameter, and this is the only way
// to put a fixture in a bucket other than CURRENT.
class ReceivablesRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ReceivablesRepository receivablesRepository;
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
    void bucketsReceivablesByAgeAndGroupsTheSummaryByPayerCategory() {
        insertFixtureReceivable(PayerCategory.CASH, 10_000, 5);
        insertFixtureReceivable(PayerCategory.CASH, 20_000, 35);
        insertFixtureReceivable(PayerCategory.NHIF, 30_000, 65);
        insertFixtureReceivable(PayerCategory.NHIF, 40_000, 95);

        List<ReceivableInvoice> all = receivablesRepository.findReceivables(null, null);
        assertThat(all).hasSize(4);
        assertThat(all).extracting(ReceivableInvoice::bucket)
                .containsExactlyInAnyOrder(AgingBucket.CURRENT, AgingBucket.DAYS_30, AgingBucket.DAYS_60, AgingBucket.DAYS_90_PLUS);

        List<ReceivableSummary> summary = receivablesRepository.summarizeAging();
        assertThat(summary).hasSize(4);
        assertThat(summary).anySatisfy(row -> {
            assertThat(row.payerCategory()).isEqualTo(PayerCategory.CASH);
            assertThat(row.bucket()).isEqualTo(AgingBucket.CURRENT);
            assertThat(row.invoiceCount()).isEqualTo(1);
            assertThat(row.totalOutstanding()).isEqualTo(new Money(10_000, "TZS"));
        });
        assertThat(summary).anySatisfy(row -> {
            assertThat(row.payerCategory()).isEqualTo(PayerCategory.NHIF);
            assertThat(row.bucket()).isEqualTo(AgingBucket.DAYS_90_PLUS);
            assertThat(row.totalOutstanding()).isEqualTo(new Money(40_000, "TZS"));
        });
    }

    @Test
    void filtersByPayerCategoryAndBucket() {
        insertFixtureReceivable(PayerCategory.CASH, 10_000, 5);
        insertFixtureReceivable(PayerCategory.NHIF, 30_000, 65);

        List<ReceivableInvoice> cashOnly = receivablesRepository.findReceivables(PayerCategory.CASH, null);
        assertThat(cashOnly).hasSize(1);
        assertThat(cashOnly.get(0).payerCategory()).isEqualTo(PayerCategory.CASH);

        List<ReceivableInvoice> days60Only = receivablesRepository.findReceivables(null, AgingBucket.DAYS_60);
        assertThat(days60Only).hasSize(1);
        assertThat(days60Only.get(0).payerCategory()).isEqualTo(PayerCategory.NHIF);
    }

    private void insertFixtureReceivable(PayerCategory payerCategory, long grossMinor, int daysAgo) {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                payerCategory, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(grossMinor, "TZS"), new Money(0, "TZS"), new Money(grossMinor, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-" + UUID.randomUUID().toString().substring(0, 6), draft.version());
        Invoice receivable = invoiceRepository.markReceivablePostDischarge(issued.id());

        jdbcTemplate.update("UPDATE invoice SET receivable_at = now() - (:daysAgo || ' days')::interval WHERE id = :id",
                new MapSqlParameterSource().addValue("id", receivable.id()).addValue("daysAgo", daysAgo));
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
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
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
