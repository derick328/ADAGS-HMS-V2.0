package com.adags;

import com.adags.modules.billing.application.ChargePostingCommand;
import com.adags.modules.billing.application.ChargePostingResult;
import com.adags.modules.billing.application.ChargePostingService;
import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.Payment;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Phase 7 item 3 part 3 — the required end-to-end proof: insured patient,
// NHIF policy VERIFIED, a 30% top-up rule (insurance covers 70%) ->
// charge posted and split -> patient pays their 30% -> gate satisfied ->
// invoice reaches PATIENT_PORTION_SETTLED -> a DRAFT InsuranceClaim is
// raised for the 70% insurance portion -> the invoice does NOT reach
// CLOSED. "Coverage satisfying the gate is not the same as the claim
// being approved."
class EndToEndInsuranceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ChargePostingService chargePostingService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private BillingGate billingGate;
    @Autowired
    private SchemeCoverageRuleRepository schemeCoverageRuleRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE insurance_claim, scheme_coverage_rule, insurance_policy, insurance_scheme,
                                 payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void anInsuredPatientProceedsOnCoverageAndRaisesAClaimWithoutClosingTheInvoice() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID schemeId = insertFixtureSchemeWithVerifiedPolicy(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(1_000_000);
        schemeCoverageRuleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE, BigDecimal.valueOf(70), false, admin));

        // -> charge posted, line split gross/insurance/patient
        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), admin));

        assertThat(posted.grossAmount()).isEqualTo(new Money(1_000_000, "TZS"));
        assertThat(posted.patientPortion()).isEqualTo(new Money(300_000, "TZS"));
        Long insurancePortionMinor = jdbcTemplate.queryForObject(
                "SELECT insurance_portion_minor FROM invoice_line WHERE id = :id",
                new MapSqlParameterSource("id", posted.invoiceLineId()), Long.class);
        assertThat(insurancePortionMinor).isEqualTo(700_000L);

        // Before payment, nothing is settled and the gate is not yet satisfied.
        assertThat(billingGate.isSatisfied(posted.invoiceLineId())).isFalse();

        // -> patient pays their 30% portion
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        Payment payment = postPaymentService.recordPayment(new PostPaymentCommand(
                patientId, PaymentMethod.CASH, null, "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(posted.invoiceLineId(), 300_000)), admin));
        assertThat(payment.amount()).isEqualTo(new Money(300_000, "TZS"));

        // -> verified_insurance_covers returns true / gate satisfied, patient proceeds
        assertThat(billingGate.isSatisfied(posted.invoiceLineId())).isTrue();
        billingGate.authorizeConsumption(posted.invoiceLineId(), admin);

        // -> invoice reaches PATIENT_PORTION_SETTLED
        String invoiceState = jdbcTemplate.queryForObject(
                "SELECT state FROM invoice WHERE id = :id",
                new MapSqlParameterSource("id", posted.invoiceId()), String.class);
        assertThat(invoiceState).isEqualTo(InvoiceState.PATIENT_PORTION_SETTLED.name());

        // -> InsuranceClaim raised in DRAFT for the 70% insurance portion
        UUID claimId = jdbcTemplate.queryForObject(
                "SELECT id FROM insurance_claim WHERE invoice_id = :invoiceId",
                new MapSqlParameterSource("invoiceId", posted.invoiceId()), UUID.class);
        String claimState = jdbcTemplate.queryForObject(
                "SELECT state FROM insurance_claim WHERE id = :id", new MapSqlParameterSource("id", claimId), String.class);
        assertThat(claimState).isEqualTo(ClaimState.DRAFT.name());
        String claimNo = jdbcTemplate.queryForObject(
                "SELECT claim_no FROM insurance_claim WHERE id = :id", new MapSqlParameterSource("id", claimId), String.class);
        assertThat(claimNo).isNotBlank();
        UUID claimSchemeId = jdbcTemplate.queryForObject(
                "SELECT scheme_id FROM insurance_claim WHERE id = :id", new MapSqlParameterSource("id", claimId), UUID.class);
        assertThat(claimSchemeId).isEqualTo(schemeId);
        Integer claimRaisedEventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'ClaimRaised' AND payload->>'invoiceId' = :id",
                new MapSqlParameterSource("id", posted.invoiceId().toString()), Integer.class);
        assertThat(claimRaisedEventCount).isEqualTo(1);

        // -> invoice does NOT reach CLOSED
        assertThat(invoiceState).isNotEqualTo(InvoiceState.CLOSED.name());
    }

    private UUID insertFixtureSchemeWithVerifiedPolicy(UUID patientId) {
        UUID schemeId = jdbcTemplate.queryForObject("""
                        INSERT INTO insurance_scheme (code, name) VALUES (:code, 'Test NHIF') RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO insurance_policy (patient_id, scheme_id, policy_number, valid_from, verification_status)
                        VALUES (:patientId, :schemeId, :policyNumber, :validFrom, 'VERIFIED')
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("schemeId", schemeId)
                        .addValue("policyNumber", "POL-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("validFrom", java.time.LocalDate.now().minusDays(10)));
        return schemeId;
    }

    // A dedicated fixture item, not the shared seeded CONSULT_GENERAL —
    // only CASH has a seeded price (V15).
    private String insertFixtureNhifPricedServiceItem(long amountMinor) {
        String code = "TEST-ITEM-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, 'Test Item', 'CONSULTATION')",
                new MapSqlParameterSource("code", code));
        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'NHIF'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
                        VALUES (:priceListId, (SELECT id FROM service_item WHERE code = :code), :amountMinor,
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("priceListId", priceListId)
                        .addValue("code", code)
                        .addValue("amountMinor", amountMinor));
        return code;
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
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'AWAITING_PAYMENT', 'INSURANCE',
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
