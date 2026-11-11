package com.adags.modules.billing;

import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.application.RequestRefundCommand;
import com.adags.modules.billing.application.RequestRefundService;
import com.adags.modules.billing.application.ReviewRefundCommand;
import com.adags.modules.billing.application.ReviewRefundService;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.Payment;
import com.adags.modules.billing.domain.PaymentAllocationRepository;
import com.adags.modules.billing.domain.PaymentAllocationNotSettledException;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.billing.domain.Refund;
import com.adags.modules.billing.domain.RefundAlreadyDecidedException;
import com.adags.modules.billing.domain.RefundExceedsAllocationException;
import com.adags.modules.billing.domain.RefundSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.RefundStatus;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 7 item 1: "Money going back out. Request -> ACCOUNTANT approves.
// Never a receptionist action. Second signature above the threshold. A
// refund can only exist against a settled payment allocation, so it
// references the Payment, not just the invoice."
class RefundTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RequestRefundService requestRefundService;
    @Autowired
    private ReviewRefundService reviewRefundService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceLineRepository invoiceLineRepository;
    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE refund, payment_allocation, payment, cashier_shift, service_authorization,
                                 invoice_line, invoice, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void belowThresholdAppliesImmediatelyAndReopensTheBalance() {
        UUID admin = bootstrapAdminId();
        SettledAllocation settled = settleFixtureInvoice(500_000, admin);

        Refund refund = requestRefundService.request(
                new RequestRefundCommand(settled.allocationId(), 200_000, "duplicate charge", admin));

        assertThat(refund.requiresSecondSignature()).isFalse();
        assertThat(refund.status()).isEqualTo(RefundStatus.APPLIED);

        Invoice reloaded = invoiceRepository.findById(settled.invoiceId()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(200_000, "TZS"));
    }

    @Test
    void aboveThresholdRequiresADistinctSecondSignature() {
        UUID admin = bootstrapAdminId();
        UUID secondSigner = insertFixtureUser();
        SettledAllocation settled = settleFixtureInvoice(30_000_000, admin);

        Refund pending = requestRefundService.request(
                new RequestRefundCommand(settled.allocationId(), 20_000_000, "wrong price refunded", admin));
        assertThat(pending.status()).isEqualTo(RefundStatus.PENDING);

        assertThatThrownBy(() -> reviewRefundService.review(
                new ReviewRefundCommand(pending.id(), RefundStatus.APPLIED, admin, "self")))
                .isInstanceOf(RefundSelfApprovalNotAllowedException.class);

        Refund applied = reviewRefundService.review(
                new ReviewRefundCommand(pending.id(), RefundStatus.APPLIED, secondSigner, "confirmed"));
        assertThat(applied.status()).isEqualTo(RefundStatus.APPLIED);

        Invoice reloaded = invoiceRepository.findById(settled.invoiceId()).orElseThrow();
        assertThat(reloaded.outstandingTotal()).isEqualTo(new Money(20_000_000, "TZS"));
    }

    @Test
    void refundingMoreThanTheAllocationIsRejected() {
        UUID admin = bootstrapAdminId();
        SettledAllocation settled = settleFixtureInvoice(500_000, admin);

        assertThatThrownBy(() -> requestRefundService.request(
                new RequestRefundCommand(settled.allocationId(), 500_001, "too much", admin)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // The database-level guard: a second refund racing to apply after the
    // first already consumed the balance is rejected at apply time, not
    // just at request time.
    @Test
    void aSecondRefundExceedingWhatRemainsAfterTheFirstIsRejectedAtTheDatabase() {
        UUID admin = bootstrapAdminId();
        SettledAllocation settled = settleFixtureInvoice(500_000, admin);
        requestRefundService.request(new RequestRefundCommand(settled.allocationId(), 400_000, "first refund", admin));

        assertThatThrownBy(() -> paymentAllocationRepository.recordRefund(settled.allocationId(), new Money(200_000, "TZS")))
                .isInstanceOf(RefundExceedsAllocationException.class);
    }

    @Test
    void requestingARefundAgainstAPendingClearanceChequeIsRejected() {
        UUID admin = bootstrapAdminId();
        Invoice issued = insertFixtureIssuedInvoice(500_000, admin);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        InvoiceLine line = invoiceLineRepository.findByInvoiceId(issued.id()).get(0);
        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                issued.patientId(), PaymentMethod.CHEQUE, "CHQ-1", "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(line.id(), 500_000)), admin));
        assertThat(payment.status().name()).isEqualTo("PENDING_CLEARANCE");
        UUID allocationId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_allocation WHERE payment_id = :paymentId",
                new MapSqlParameterSource("paymentId", payment.id()), UUID.class);

        assertThatThrownBy(() -> requestRefundService.request(
                new RequestRefundCommand(allocationId, 500_000, "too early", admin)))
                .isInstanceOf(PaymentAllocationNotSettledException.class);
    }

    @Test
    void reviewingAnAlreadyDecidedRefundIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID secondSigner = insertFixtureUser();
        SettledAllocation settled = settleFixtureInvoice(30_000_000, admin);
        Refund pending = requestRefundService.request(
                new RequestRefundCommand(settled.allocationId(), 20_000_000, "wrong price", admin));
        reviewRefundService.review(new ReviewRefundCommand(pending.id(), RefundStatus.REJECTED, secondSigner, "no"));

        assertThatThrownBy(() -> reviewRefundService.review(
                new ReviewRefundCommand(pending.id(), RefundStatus.APPLIED, secondSigner, null)))
                .isInstanceOf(RefundAlreadyDecidedException.class);
    }

    // root CLAUDE.md "Authorisation": the second-person constraint lives
    // at the database, not just the service layer.
    @Test
    void theDatabaseRejectsSelfApprovalEvenBypassingTheServiceLayer() {
        UUID admin = bootstrapAdminId();
        SettledAllocation settled = settleFixtureInvoice(30_000_000, admin);
        Refund pending = requestRefundService.request(
                new RequestRefundCommand(settled.allocationId(), 20_000_000, "wrong price", admin));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE refund SET status = 'APPLIED', decided_by = :admin, decided_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", pending.id()).addValue("admin", admin)))
                .isInstanceOf(DataAccessException.class);
    }

    private record SettledAllocation(UUID invoiceId, UUID allocationId) {
    }

    private SettledAllocation settleFixtureInvoice(long amountMinor, UUID admin) {
        Invoice issued = insertFixtureIssuedInvoice(amountMinor, admin);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        InvoiceLine line = invoiceLineRepository.findByInvoiceId(issued.id()).get(0);
        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                issued.patientId(), PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(line.id(), amountMinor)), admin));
        UUID allocationId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_allocation WHERE payment_id = :paymentId",
                new MapSqlParameterSource("paymentId", payment.id()), UUID.class);
        return new SettledAllocation(issued.id(), allocationId);
    }

    private Invoice insertFixtureIssuedInvoice(long grossMinor, UUID admin) {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, admin));
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
