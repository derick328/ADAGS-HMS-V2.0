package com.adags.modules.billing;

import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.application.RequestInvoiceCancellationCommand;
import com.adags.modules.billing.application.RequestInvoiceCancellationService;
import com.adags.modules.billing.application.RequestRefundCommand;
import com.adags.modules.billing.application.RequestRefundService;
import com.adags.modules.billing.application.ReviewInvoiceCancellationCommand;
import com.adags.modules.billing.application.ReviewInvoiceCancellationService;
import com.adags.modules.billing.domain.CancellationRequestStatus;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceAlreadyHasPendingCancellationException;
import com.adags.modules.billing.domain.InvoiceCancellationRequest;
import com.adags.modules.billing.domain.InvoiceCancellationRequestAlreadyDecidedException;
import com.adags.modules.billing.domain.InvoiceCancellationRequiresRefundException;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceNotPayableException;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.Payment;
import com.adags.modules.billing.domain.PaymentMethod;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 7 item 2: 18-admin.md §6. "Rejection returns the invoice to
// ISSUED — live and collectable... never left frozen after a decision."
// "Once payment has landed, void is blocked — routed to the accountant's
// refund flow." Reconsideration of a rejected request is a new row
// referencing the first, never a reopened decision.
class InvoiceCancellationTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RequestInvoiceCancellationService requestInvoiceCancellationService;
    @Autowired
    private ReviewInvoiceCancellationService reviewInvoiceCancellationService;
    @Autowired
    private RequestRefundService requestRefundService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceLineRepository invoiceLineRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE invoice_cancellation_request, refund, payment_allocation, payment, cashier_shift,
                                 service_authorization, invoice_line, invoice, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void requestingFreezesTheInvoiceAndBlocksPayment() {
        UUID admin = bootstrapAdminId();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);

        InvoiceCancellationRequest request = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "duplicate invoice", admin, null));

        assertThat(request.status()).isEqualTo(CancellationRequestStatus.PENDING);
        Invoice reloaded = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(reloaded.state()).isEqualTo(InvoiceState.CANCELLATION_PENDING);

        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        InvoiceLine line = invoiceLineRepository.findByInvoiceId(issued.id()).get(0);
        assertThatThrownBy(() -> postPaymentService.recordPayment(new PostPaymentCommand(
                issued.patientId(), PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(line.id(), 15_000)), admin)))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectionReturnsTheInvoiceToIssuedAndRequiresANote() {
        UUID admin = bootstrapAdminId();
        UUID receptionist = insertFixtureUser();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);
        InvoiceCancellationRequest request = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "duplicate invoice", receptionist, null));

        assertThatThrownBy(() -> reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(request.id(), CancellationRequestStatus.REJECTED, admin, null)))
                .isInstanceOf(IllegalArgumentException.class);

        InvoiceCancellationRequest rejected = reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(request.id(), CancellationRequestStatus.REJECTED, admin, "not a duplicate"));

        assertThat(rejected.status()).isEqualTo(CancellationRequestStatus.REJECTED);
        Invoice reloaded = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(reloaded.state()).isEqualTo(InvoiceState.ISSUED);
    }

    @Test
    void approvingAnUnpaidInvoiceVoidsIt() {
        UUID admin = bootstrapAdminId();
        UUID receptionist = insertFixtureUser();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);
        InvoiceCancellationRequest request = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "wrong patient", receptionist, null));

        InvoiceCancellationRequest approved = reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(request.id(), CancellationRequestStatus.APPROVED, admin, "confirmed"));

        assertThat(approved.status()).isEqualTo(CancellationRequestStatus.APPROVED);
        Invoice reloaded = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(reloaded.state()).isEqualTo(InvoiceState.VOID);
        assertThat(reloaded.voidReason()).isEqualTo("wrong patient");
    }

    // §10 / §6.2: "Once payment has landed, void is blocked... routed to
    // the accountant's refund flow." Approval is rejected, not silently
    // converted; refunding first and re-approving succeeds.
    @Test
    void approvingAPaidInvoiceIsBlockedUntilRefundedThenSucceeds() {
        UUID admin = bootstrapAdminId();
        UUID receptionist = insertFixtureUser();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        InvoiceLine line = invoiceLineRepository.findByInvoiceId(issued.id()).get(0);
        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                issued.patientId(), PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(line.id(), 15_000)), admin));

        InvoiceCancellationRequest request = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "wrong price charged", receptionist, null));

        assertThatThrownBy(() -> reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(request.id(), CancellationRequestStatus.APPROVED, admin, "confirmed")))
                .isInstanceOf(InvoiceCancellationRequiresRefundException.class);

        Invoice stillFrozen = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(stillFrozen.state()).isEqualTo(InvoiceState.CANCELLATION_PENDING);

        UUID allocationId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_allocation WHERE payment_id = :paymentId",
                new MapSqlParameterSource("paymentId", payment.id()), UUID.class);
        requestRefundService.request(new RequestRefundCommand(allocationId, 15_000, "cancellation refund", admin));

        InvoiceCancellationRequest approved = reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(request.id(), CancellationRequestStatus.APPROVED, admin, "refunded, now void"));

        assertThat(approved.status()).isEqualTo(CancellationRequestStatus.APPROVED);
        Invoice voided = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(voided.state()).isEqualTo(InvoiceState.VOID);
    }

    @Test
    void reconsiderationMustReferenceARejectedRequest() {
        UUID admin = bootstrapAdminId();
        UUID receptionist = insertFixtureUser();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);
        InvoiceCancellationRequest pending = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "first attempt", receptionist, null));

        assertThatThrownBy(() -> requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "reconsider", admin, pending.id())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconsiderationAfterRejectionRaisesANewRequestReferencingTheFirst() {
        UUID admin = bootstrapAdminId();
        UUID receptionist = insertFixtureUser();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);
        InvoiceCancellationRequest first = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "first attempt", receptionist, null));
        reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(first.id(), CancellationRequestStatus.REJECTED, admin, "not justified"));

        InvoiceCancellationRequest reconsidered = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "admin changed their mind", admin, first.id()));

        assertThat(reconsidered.supersedesRequestId()).isEqualTo(first.id());
        assertThat(reconsidered.status()).isEqualTo(CancellationRequestStatus.PENDING);
        Invoice reloaded = invoiceRepository.findById(issued.id()).orElseThrow();
        assertThat(reloaded.state()).isEqualTo(InvoiceState.CANCELLATION_PENDING);
    }

    @Test
    void aSecondRequestAgainstAnAlreadyPendingInvoiceIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID receptionist = insertFixtureUser();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);
        requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "first", receptionist, null));

        assertThatThrownBy(() -> requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "second", receptionist, null)))
                .isInstanceOf(InvoiceAlreadyHasPendingCancellationException.class);
    }

    @Test
    void reviewingAnAlreadyDecidedRequestIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID receptionist = insertFixtureUser();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);
        InvoiceCancellationRequest request = requestInvoiceCancellationService.request(
                new RequestInvoiceCancellationCommand(issued.id(), "first", receptionist, null));
        reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(request.id(), CancellationRequestStatus.REJECTED, admin, "no"));

        assertThatThrownBy(() -> reviewInvoiceCancellationService.review(
                new ReviewInvoiceCancellationCommand(request.id(), CancellationRequestStatus.APPROVED, admin, "changed my mind")))
                .isInstanceOf(InvoiceCancellationRequestAlreadyDecidedException.class);
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
                new MapSqlParameterSource("username", "test-receptionist-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
