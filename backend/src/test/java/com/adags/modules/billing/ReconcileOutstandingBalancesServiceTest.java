package com.adags.modules.billing;

import com.adags.modules.billing.application.ReconcileOutstandingBalancesService;
import com.adags.modules.billing.domain.BalanceDrift;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
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

// 02-billing-architecture.md §9.3: "a nightly job recomputing from source
// rows and alerting on drift. Trust the derived value for display; trust
// the ledger for truth." Drift is manufactured here by writing directly
// to outstanding_total_minor, bypassing every mechanism that normally
// keeps it honest — exactly the kind of divergence the job exists to catch.
class ReconcileOutstandingBalancesServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ReconcileOutstandingBalancesService reconcileOutstandingBalancesService;
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
    void correctsAnInvoiceWhoseStoredOutstandingDisagreesWithTheLedger() {
        Invoice issued = insertFixtureIssuedInvoice(1_000_000);
        jdbcTemplate.update("UPDATE invoice SET outstanding_total_minor = 999 WHERE id = :id",
                new MapSqlParameterSource("id", issued.id()));

        List<BalanceDrift> drifted = reconcileOutstandingBalancesService.reconcile();

        assertThat(drifted).anySatisfy(d -> {
            assertThat(d.invoiceId()).isEqualTo(issued.id());
            assertThat(d.storedOutstandingMinor()).isEqualTo(999);
            assertThat(d.recomputedOutstandingMinor()).isEqualTo(1_000_000);
        });

        Invoice reloaded = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(1_000_000, "TZS"));
    }

    @Test
    void leavesAnAlreadyCorrectInvoiceAlone() {
        Invoice issued = insertFixtureIssuedInvoice(1_000_000);

        List<BalanceDrift> drifted = reconcileOutstandingBalancesService.reconcile();

        assertThat(drifted).noneMatch(d -> d.invoiceId().equals(issued.id()));
    }

    private Invoice insertFixtureIssuedInvoice(long grossMinor) {
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
