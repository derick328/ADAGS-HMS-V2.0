package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.CashierShiftNotOpenException;
import com.adags.modules.billing.domain.InvoiceNotPayableException;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.Payment;
import com.adags.modules.billing.domain.PaymentAllocationRejectedException;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentBypassSource;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.billing.domain.PaymentStatus;
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

// 02-billing-architecture.md §9, 03-billing-addendum.md §1. Never
// truncates service_item/price_list/price_list_entry or department —
// backend/CLAUDE.md's TRUNCATE CASCADE lesson.
class PostPaymentServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private PostPaymentService postPaymentService;

    @Autowired
    private ChargePostingService chargePostingService;

    @Autowired
    private OpenCashierShiftService openCashierShiftService;

    @Autowired
    private BillingGate billingGate;

    @Autowired
    private PaymentBypassRepository paymentBypassRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, payment_bypass, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void exactPaymentSettlesTheLineAndTheInvoiceAndAuthorizesConsumption() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);

        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 10_000)),
                admin));

        assertThat(payment.status()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(payment.amount()).isEqualTo(new Money(10_000, "TZS"));

        assertThat(billingGate.isSatisfied(charge.invoiceLineId())).isTrue();

        Integer authCount = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM service_authorization
                        WHERE invoice_line_id = :lineId AND authorization_type = 'PAYMENT' AND reference_id = :paymentId
                        """,
                new MapSqlParameterSource().addValue("lineId", charge.invoiceLineId()).addValue("paymentId", payment.id()),
                Integer.class);
        assertThat(authCount).isEqualTo(1);

        String invoiceState = jdbcTemplate.queryForObject(
                "SELECT state FROM invoice WHERE id = :id", new MapSqlParameterSource("id", charge.invoiceId()), String.class);
        assertThat(invoiceState).isEqualTo(InvoiceState.PATIENT_PORTION_SETTLED.name());

        Long outstanding = jdbcTemplate.queryForObject(
                "SELECT outstanding_total_minor FROM invoice WHERE id = :id",
                new MapSqlParameterSource("id", charge.invoiceId()), Long.class);
        assertThat(outstanding).isZero();

        Integer settledEventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'InvoiceSettled' AND payload->>'invoiceId' = :id",
                new MapSqlParameterSource("id", charge.invoiceId().toString()), Integer.class);
        assertThat(settledEventCount).isEqualTo(1);
    }

    @Test
    void aRepeatIdempotencyKeyReturnsTheOriginalPaymentAndDoesNotDoubleAuthorize() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);
        String idempotencyKey = "idem-" + UUID.randomUUID();
        PostPaymentCommand command = new PostPaymentCommand(patientId, PaymentMethod.CASH, null, idempotencyKey,
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 10_000)), admin);

        Payment first = postPaymentService.recordPayment(command);
        Payment second = postPaymentService.recordPayment(command);

        assertThat(second.id()).isEqualTo(first.id());
        Integer paymentCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment WHERE idempotency_key = :key",
                new MapSqlParameterSource("key", idempotencyKey), Integer.class);
        assertThat(paymentCount).isEqualTo(1);
        Integer authCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM service_authorization WHERE invoice_line_id = :id",
                new MapSqlParameterSource("id", charge.invoiceLineId()), Integer.class);
        assertThat(authCount).isEqualTo(1);
    }

    @Test
    void noOpenShiftFailsWithAClearPrompt() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);

        assertThatThrownBy(() -> postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 10_000)), admin)))
                .isInstanceOf(CashierShiftNotOpenException.class);
    }

    // 03-billing-addendum.md §1: "No partial payment on any single
    // line... anything less is REJECTED" without an active bypass.
    @Test
    void partialPaymentWithoutABypassIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);

        assertThatThrownBy(() -> postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 5_000)), admin)))
                .isInstanceOf(PaymentAllocationRejectedException.class);
    }

    // The emergency exception: partial is accepted under an active
    // bypass, and the remainder accrues (stays outstanding).
    @Test
    void partialPaymentUnderAnActiveBypassIsAcceptedAndTheRemainderAccrues() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        paymentBypassRepository.insert(
                PaymentBypass.grant(encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, "IMMEDIATE", admin));
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);

        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 3_000)), admin));

        assertThat(payment.amount()).isEqualTo(new Money(3_000, "TZS"));

        Long outstanding = jdbcTemplate.queryForObject(
                "SELECT outstanding_total_minor FROM invoice WHERE id = :id",
                new MapSqlParameterSource("id", charge.invoiceId()), Long.class);
        assertThat(outstanding).isEqualTo(7_000L);

        String invoiceState = jdbcTemplate.queryForObject(
                "SELECT state FROM invoice WHERE id = :id", new MapSqlParameterSource("id", charge.invoiceId()), String.class);
        assertThat(invoiceState).isEqualTo(InvoiceState.ISSUED.name());

        // The line still fulfils — via the bypass clause, not payment.
        assertThat(billingGate.isSatisfied(charge.invoiceLineId())).isTrue();
    }

    @Test
    void paymentAgainstAVoidInvoiceIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);
        jdbcTemplate.update("""
                        UPDATE invoice SET state = 'VOID', void_reason = 'test', voided_by = :admin, voided_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", charge.invoiceId()).addValue("admin", admin));

        assertThatThrownBy(() -> postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 10_000)), admin)))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void aChequePaymentSettlesAsPendingClearanceAndDoesNotOpenTheGateYet() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);

        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CHEQUE, "CHQ-00123", "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 10_000)), admin));

        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING_CLEARANCE);
        assertThat(billingGate.isSatisfied(charge.invoiceLineId())).isFalse();

        Long outstanding = jdbcTemplate.queryForObject(
                "SELECT outstanding_total_minor FROM invoice WHERE id = :id",
                new MapSqlParameterSource("id", charge.invoiceId()), Long.class);
        assertThat(outstanding).isEqualTo(10_000L);
    }

    // The DB CHECK behind §9.2's "reference required for every non-cash method."
    @Test
    void aNonCashPaymentWithoutAReferenceIsRejectedByTheDatabase() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult charge = postConsultCharge(encounterId, patientId, admin);

        assertThatThrownBy(() -> postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.MOBILE_MONEY, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(charge.invoiceLineId(), 10_000)), admin)))
                .isInstanceOf(DataAccessException.class);
    }

    private ChargePostingResult postConsultCharge(UUID encounterId, UUID patientId, UUID admin) {
        return chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TriageCompleted", UUID.randomUUID(), admin));
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
