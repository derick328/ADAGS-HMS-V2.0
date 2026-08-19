package com.adags.modules.billing;

import com.adags.modules.billing.application.RequestInvoiceWriteOffCommand;
import com.adags.modules.billing.application.RequestInvoiceWriteOffService;
import com.adags.modules.billing.application.ReviewInvoiceWriteOffCommand;
import com.adags.modules.billing.application.ReviewInvoiceWriteOffService;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceNotWriteOffEligibleException;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceWriteOff;
import com.adags.modules.billing.domain.InvoiceWriteOffAlreadyDecidedException;
import com.adags.modules.billing.domain.InvoiceWriteOffSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.WriteOffStatus;
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

// Phase 7 item 1: "Write-off with reason, ACCOUNTANT authority, second
// signature above a configurable threshold." The seeded threshold (V43) is
// 10,000,000 minor (100,000/=) — amounts below it self-authorize at
// request time, amounts above it land PENDING for a second, distinct
// accountant.
class InvoiceWriteOffTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RequestInvoiceWriteOffService requestInvoiceWriteOffService;
    @Autowired
    private ReviewInvoiceWriteOffService reviewInvoiceWriteOffService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceLineRepository invoiceLineRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "TRUNCATE invoice_write_off, service_authorization, invoice_line, invoice, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void belowThresholdAppliesImmediatelyAndSelfAuthorizes() {
        Invoice receivable = insertFixtureReceivable(30_000_000);
        UUID accountant = bootstrapAdminId();

        InvoiceWriteOff writeOff = requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(receivable.id(), 500_000, "goodwill adjustment", accountant));

        assertThat(writeOff.requiresSecondSignature()).isFalse();
        assertThat(writeOff.status()).isEqualTo(WriteOffStatus.APPLIED);
        assertThat(writeOff.decidedBy()).isEqualTo(accountant);

        Invoice reloaded = invoiceRepository.findById(receivable.id()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(29_500_000, "TZS"));
    }

    @Test
    void aboveThresholdLandsPendingAndDoesNotTouchTheBalanceUntilDecided() {
        Invoice receivable = insertFixtureReceivable(30_000_000);
        UUID accountant = bootstrapAdminId();

        InvoiceWriteOff writeOff = requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(receivable.id(), 20_000_000, "uncollectable", accountant));

        assertThat(writeOff.requiresSecondSignature()).isTrue();
        assertThat(writeOff.status()).isEqualTo(WriteOffStatus.PENDING);

        Invoice reloaded = invoiceRepository.findById(receivable.id()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(30_000_000, "TZS"));
    }

    @Test
    void aSecondDistinctAccountantCanApplyAPendingWriteOff() {
        Invoice receivable = insertFixtureReceivable(30_000_000);
        UUID requester = bootstrapAdminId();
        UUID secondSigner = insertFixtureUser();

        InvoiceWriteOff pending = requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(receivable.id(), 20_000_000, "uncollectable", requester));

        InvoiceWriteOff applied = reviewInvoiceWriteOffService.review(
                new ReviewInvoiceWriteOffCommand(pending.id(), WriteOffStatus.APPLIED, secondSigner, "confirmed uncollectable"));

        assertThat(applied.status()).isEqualTo(WriteOffStatus.APPLIED);
        assertThat(applied.decidedBy()).isEqualTo(secondSigner);
        Invoice reloaded = invoiceRepository.findById(receivable.id()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(10_000_000, "TZS"));
    }

    @Test
    void theRequesterCannotAlsoBeTheSecondSignature() {
        Invoice receivable = insertFixtureReceivable(30_000_000);
        UUID requester = bootstrapAdminId();

        InvoiceWriteOff pending = requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(receivable.id(), 20_000_000, "uncollectable", requester));

        assertThatThrownBy(() -> reviewInvoiceWriteOffService.review(
                new ReviewInvoiceWriteOffCommand(pending.id(), WriteOffStatus.APPLIED, requester, "self-approving")))
                .isInstanceOf(InvoiceWriteOffSelfApprovalNotAllowedException.class);

        Invoice reloaded = invoiceRepository.findById(receivable.id()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).as("self-approval must not touch the balance").isEqualTo(new Money(30_000_000, "TZS"));
    }

    // root CLAUDE.md "Authorisation": the second-person constraint lives at
    // the database, not just in ReviewInvoiceWriteOffService — proven here
    // by going around the service entirely.
    @Test
    void theDatabaseRejectsSelfApprovalEvenBypassingTheServiceLayer() {
        Invoice receivable = insertFixtureReceivable(30_000_000);
        UUID requester = bootstrapAdminId();
        InvoiceWriteOff pending = requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(receivable.id(), 20_000_000, "uncollectable", requester));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE invoice_write_off SET status = 'APPLIED', decided_by = :requester, decided_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", pending.id()).addValue("requester", requester)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void reviewingAnAlreadyDecidedWriteOffIsRejected() {
        Invoice receivable = insertFixtureReceivable(30_000_000);
        UUID requester = bootstrapAdminId();
        UUID secondSigner = insertFixtureUser();
        InvoiceWriteOff pending = requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(receivable.id(), 20_000_000, "uncollectable", requester));
        reviewInvoiceWriteOffService.review(
                new ReviewInvoiceWriteOffCommand(pending.id(), WriteOffStatus.REJECTED, secondSigner, "keep chasing"));

        assertThatThrownBy(() -> reviewInvoiceWriteOffService.review(
                new ReviewInvoiceWriteOffCommand(pending.id(), WriteOffStatus.APPLIED, secondSigner, null)))
                .isInstanceOf(InvoiceWriteOffAlreadyDecidedException.class);
    }

    @Test
    void requestingAWriteOffAgainstAnIssuedInvoiceIsRejected() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(15_000, "TZS"), new Money(0, "TZS"), new Money(15_000, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-000020", draft.version());

        assertThatThrownBy(() -> requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(issued.id(), 5_000, "too early", bootstrapAdminId())))
                .isInstanceOf(InvoiceNotWriteOffEligibleException.class);
    }

    @Test
    void requestingMoreThanOutstandingIsRejected() {
        Invoice receivable = insertFixtureReceivable(30_000_000);

        assertThatThrownBy(() -> requestInvoiceWriteOffService.request(
                new RequestInvoiceWriteOffCommand(receivable.id(), 30_000_001, "too much", bootstrapAdminId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Invoice insertFixtureReceivable(long grossMinor) {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, bootstrapAdminId()));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(grossMinor, "TZS"), new Money(0, "TZS"), new Money(grossMinor, "TZS"),
                "TriageCompleted", encounterId));
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-" + UUID.randomUUID().toString().substring(0, 6), draft.version());
        return invoiceRepository.markReceivablePostDischarge(issued.id());
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
