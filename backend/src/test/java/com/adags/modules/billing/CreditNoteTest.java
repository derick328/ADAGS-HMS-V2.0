package com.adags.modules.billing;

import com.adags.modules.billing.application.IssueCreditNoteCommand;
import com.adags.modules.billing.application.IssueCreditNoteService;
import com.adags.modules.billing.application.ReviewCreditNoteCommand;
import com.adags.modules.billing.application.ReviewCreditNoteService;
import com.adags.modules.billing.domain.CreditNote;
import com.adags.modules.billing.domain.CreditNoteAlreadyDecidedException;
import com.adags.modules.billing.domain.CreditNoteNotEligibleException;
import com.adags.modules.billing.domain.CreditNoteSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.CreditNoteStatus;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 7 item 1: "Reduces what a patient owes without money moving. Used
// when a service was charged but not delivered, a price was wrong, or a
// goodwill adjustment is agreed. ACCOUNTANT authority, reason required.
// CHECK (approved_by != issued_by) above the same configurable threshold
// as write-offs." Unlike write-off, eligible against an ISSUED invoice
// too — never touches invoice_line.
class CreditNoteTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private IssueCreditNoteService issueCreditNoteService;
    @Autowired
    private ReviewCreditNoteService reviewCreditNoteService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceLineRepository invoiceLineRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "TRUNCATE credit_note, service_authorization, invoice_line, invoice, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void belowThresholdAppliesImmediatelyWithoutTouchingTheInvoiceLine() {
        Invoice issued = insertFixtureIssuedInvoice(30_000_000);
        UUID accountant = bootstrapAdminId();

        CreditNote creditNote = issueCreditNoteService.issue(
                new IssueCreditNoteCommand(issued.id(), 500_000, "malaria RDT not actually performed", "note-1", accountant));

        assertThat(creditNote.requiresSecondSignature()).isFalse();
        assertThat(creditNote.status()).isEqualTo(CreditNoteStatus.APPLIED);
        assertThat(creditNote.approvedBy()).isEqualTo(accountant);

        Invoice reloaded = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(29_500_000, "TZS"));

        Integer lineCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice_line WHERE invoice_id = :id",
                new MapSqlParameterSource("id", issued.id()), Integer.class);
        assertThat(lineCount).as("credit note never touches invoice_line").isEqualTo(1);
    }

    @Test
    void aboveThresholdRequiresADistinctSecondSignature() {
        Invoice issued = insertFixtureIssuedInvoice(30_000_000);
        UUID issuer = bootstrapAdminId();
        UUID secondSigner = insertFixtureUser();

        CreditNote pending = issueCreditNoteService.issue(
                new IssueCreditNoteCommand(issued.id(), 20_000_000, "wrong price on the whole invoice", null, issuer));
        assertThat(pending.status()).isEqualTo(CreditNoteStatus.PENDING);

        assertThatThrownBy(() -> reviewCreditNoteService.review(
                new ReviewCreditNoteCommand(pending.id(), CreditNoteStatus.APPLIED, issuer, "self-approving")))
                .isInstanceOf(CreditNoteSelfApprovalNotAllowedException.class);

        CreditNote applied = reviewCreditNoteService.review(
                new ReviewCreditNoteCommand(pending.id(), CreditNoteStatus.APPLIED, secondSigner, "confirmed"));
        assertThat(applied.status()).isEqualTo(CreditNoteStatus.APPLIED);

        Invoice reloaded = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(10_000_000, "TZS"));
    }

    // root CLAUDE.md "Authorisation": the second-person constraint lives
    // at the database, not just the service layer.
    @Test
    void theDatabaseRejectsSelfApprovalEvenBypassingTheServiceLayer() {
        Invoice issued = insertFixtureIssuedInvoice(30_000_000);
        UUID issuer = bootstrapAdminId();
        CreditNote pending = issueCreditNoteService.issue(
                new IssueCreditNoteCommand(issued.id(), 20_000_000, "wrong price", null, issuer));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE credit_note SET status = 'APPLIED', approved_by = :issuer, approved_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", pending.id()).addValue("issuer", issuer)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void reviewingAnAlreadyDecidedCreditNoteIsRejected() {
        Invoice issued = insertFixtureIssuedInvoice(30_000_000);
        UUID issuer = bootstrapAdminId();
        UUID secondSigner = insertFixtureUser();
        CreditNote pending = issueCreditNoteService.issue(
                new IssueCreditNoteCommand(issued.id(), 20_000_000, "wrong price", null, issuer));
        reviewCreditNoteService.review(new ReviewCreditNoteCommand(pending.id(), CreditNoteStatus.REJECTED, secondSigner, "no"));

        assertThatThrownBy(() -> reviewCreditNoteService.review(
                new ReviewCreditNoteCommand(pending.id(), CreditNoteStatus.APPLIED, secondSigner, null)))
                .isInstanceOf(CreditNoteAlreadyDecidedException.class);
    }

    @Test
    void aVoidInvoiceIsNotEligible() {
        Invoice issued = insertFixtureIssuedInvoice(15_000);
        Invoice voided = invoiceRepository.voidUnpaid(issued.id(), "test void", bootstrapAdminId());
        assertThat(voided.state().name()).isEqualTo("VOID");

        assertThatThrownBy(() -> issueCreditNoteService.issue(
                new IssueCreditNoteCommand(issued.id(), 5_000, "too late", null, bootstrapAdminId())))
                .isInstanceOf(CreditNoteNotEligibleException.class);
    }

    @Test
    void issuingMoreThanOutstandingIsRejected() {
        Invoice issued = insertFixtureIssuedInvoice(15_000);

        assertThatThrownBy(() -> issueCreditNoteService.issue(
                new IssueCreditNoteCommand(issued.id(), 15_001, "too much", null, bootstrapAdminId())))
                .isInstanceOf(IllegalArgumentException.class);
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

    private UUID insertFixtureUser() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", "test-accountant-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
