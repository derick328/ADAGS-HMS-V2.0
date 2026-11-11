package com.adags.modules.billing;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.InvoiceWriteOffExceedsOutstandingException;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.StaleInvoiceException;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §10: two-step issue. Never truncates
// service_item/price_list/price_list_entry (the seeded CASH prices other
// tests rely on) or department — backend/CLAUDE.md's TRUNCATE CASCADE
// lesson.
class InvoiceRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

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
    void insertDraftStartsAtZeroWithNoInvoiceNumber() {
        UUID patientId = insertFixturePatient();

        Invoice draft = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null, bootstrapAdminId()));

        assertThat(draft.id()).isNotNull();
        assertThat(draft.state()).isEqualTo(InvoiceState.DRAFT);
        assertThat(draft.invoiceNumber()).isNull();
        assertThat(draft.grossTotal()).isEqualTo(new Money(0, "TZS"));
        assertThat(draft.version()).isZero();
    }

    // Invoice.encounterId is nullable — 02-billing-architecture.md §11: "a
    // pharmacy walk-in or a records fee has no encounter."
    @Test
    void aDraftInvoiceMayHaveNoEncounter() {
        UUID patientId = insertFixturePatient();

        Invoice draft = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null, bootstrapAdminId()));

        assertThat(draft.encounterId()).isNull();
    }

    @Test
    void issueAssignsANumberAndDerivesTotalsFromLines() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 2,
                new Money(20_000, "TZS"), new Money(0, "TZS"), new Money(20_000, "TZS"),
                "TriageCompleted", encounterId));

        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-000001", draft.version());

        assertThat(issued.state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(issued.invoiceNumber()).isEqualTo("FAC-2026-000001");
        assertThat(issued.grossTotal()).isEqualTo(new Money(20_000, "TZS"));
        assertThat(issued.patientPortionTotal()).isEqualTo(new Money(20_000, "TZS"));
        assertThat(issued.version()).isEqualTo(draft.version() + 1);
    }

    @Test
    void issuingAtAStaleVersionIsRejected() {
        UUID patientId = insertFixturePatient();
        Invoice draft = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null, bootstrapAdminId()));

        assertThatThrownBy(() -> invoiceRepository.issue(draft.id(), "FAC-2026-000002", draft.version() + 1))
                .isInstanceOf(StaleInvoiceException.class);
    }

    // Two-step issue: a second issue attempt against an already-ISSUED
    // invoice must not silently re-run (which would re-derive totals and
    // bump the version again) or double-allocate a number.
    @Test
    void issuingAnAlreadyIssuedInvoiceIsRejected() {
        UUID patientId = insertFixturePatient();
        Invoice draft = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null, bootstrapAdminId()));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-000003", draft.version());

        assertThatThrownBy(() -> invoiceRepository.issue(draft.id(), "FAC-2026-000004", issued.version()))
                .isInstanceOf(StaleInvoiceException.class);
    }

    // Phase 7 item 1: RECEIVABLE_POST_DISCHARGE is §8's outcome of an
    // unpaid balance surviving discharge; receivableAt is the aging clock
    // the aged-receivables report buckets from.
    @Test
    void markReceivablePostDischargeStampsTheAgingClock() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(15_000, "TZS"), new Money(0, "TZS"), new Money(15_000, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-000010", draft.version());
        assertThat(issued.receivableAt()).isNull();

        Instant before = Instant.now();
        Invoice receivable = invoiceRepository.markReceivablePostDischarge(issued.id());

        assertThat(receivable.state()).isEqualTo(InvoiceState.RECEIVABLE_POST_DISCHARGE);
        assertThat(receivable.receivableAt()).isNotNull().isAfterOrEqualTo(before);
    }

    @Test
    void recordWriteOffDecrementsOutstandingOnAReceivable() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(15_000, "TZS"), new Money(0, "TZS"), new Money(15_000, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-000011", draft.version());
        Invoice receivable = invoiceRepository.markReceivablePostDischarge(issued.id());

        Invoice writtenOff = invoiceRepository.recordWriteOff(receivable.id(), new Money(5_000, "TZS"));

        assertThat(writtenOff.outstandingTotal()).isEqualTo(new Money(10_000, "TZS"));
    }

    @Test
    void recordWriteOffAboveOutstandingIsRejected() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(15_000, "TZS"), new Money(0, "TZS"), new Money(15_000, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-000012", draft.version());
        Invoice receivable = invoiceRepository.markReceivablePostDischarge(issued.id());

        assertThatThrownBy(() -> invoiceRepository.recordWriteOff(receivable.id(), new Money(15_001, "TZS")))
                .isInstanceOf(InvoiceWriteOffExceedsOutstandingException.class);
    }

    @Test
    void recordWriteOffOnAnIssuedInvoiceThatIsNotYetAReceivableIsRejected() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(15_000, "TZS"), new Money(0, "TZS"), new Money(15_000, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-000013", draft.version());

        assertThatThrownBy(() -> invoiceRepository.recordWriteOff(issued.id(), new Money(5_000, "TZS")))
                .isInstanceOf(InvoiceWriteOffExceedsOutstandingException.class);
    }

    // 05-receptionist.md §7.2/§8 and the admin dashboard's "Unpaid at desk"
    // tile: outstandingOnly must exclude a DRAFT (outstanding is always
    // zero pre-issue) and an ISSUED-but-fully-unlined invoice, and include
    // only an ISSUED invoice actually carrying a patient portion.
    @Test
    void searchWithOutstandingOnlyExcludesDraftsAndZeroBalanceInvoices() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();

        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));

        Invoice toIssue = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(toIssue.id(), serviceItemId, 1,
                new Money(10_000, "TZS"), new Money(0, "TZS"), new Money(10_000, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issuedWithBalance = invoiceRepository.issue(toIssue.id(), "FAC-2026-SEARCH-1", toIssue.version());

        var results = invoiceRepository.search(patientId, null, null, null, null, true);

        assertThat(results).extracting(Invoice::id).containsExactly(issuedWithBalance.id());
        assertThat(results).extracting(Invoice::id).doesNotContain(draft.id());
    }

    @Test
    void searchFiltersByCreatedRange() {
        UUID patientId = insertFixturePatient();
        Invoice draft = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null, bootstrapAdminId()));

        var withinRange = invoiceRepository.search(patientId, null, null,
                draft.createdAt().minusSeconds(60), draft.createdAt().plusSeconds(60), false);
        var outsideRange = invoiceRepository.search(patientId, null, null,
                draft.createdAt().plusSeconds(3600), draft.createdAt().plusSeconds(7200), false);

        assertThat(withinRange).extracting(Invoice::id).contains(draft.id());
        assertThat(outsideRange).extracting(Invoice::id).doesNotContain(draft.id());
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
