package com.adags.modules.reporting;

import com.adags.modules.billing.application.ChargePostingCommand;
import com.adags.modules.billing.application.ChargePostingResult;
import com.adags.modules.billing.application.ChargePostingService;
import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.insurance.application.TransitionInsuranceClaimCommand;
import com.adags.modules.insurance.application.TransitionInsuranceClaimService;
import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.modules.reporting.application.ClaimsReportService;
import com.adags.modules.reporting.application.ClaimsSummaryLine;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 02-billing-architecture.md §13: "Claims are billed, not collected" —
// "so the accountant can see what is owed by whom and chase rejected
// claims."
class ClaimsReportServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ClaimsReportService claimsReportService;
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
    private TransitionInsuranceClaimService transitionInsuranceClaimService;
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
    void groupsByStateWithCountAndInsuranceAmount() {
        UUID admin = bootstrapAdminId();
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        UUID draftClaimId = raiseClaim(admin, 700_000);
        UUID submittedClaimId = raiseClaim(admin, 700_000);
        transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(submittedClaimId, ClaimState.SUBMITTED, null));

        List<ClaimsSummaryLine> summary = claimsReportService.summarize(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(summary).extracting(ClaimsSummaryLine::state).containsExactlyInAnyOrder("DRAFT", "SUBMITTED");
        ClaimsSummaryLine draftLine = summary.stream().filter(l -> l.state().equals("DRAFT")).findFirst().orElseThrow();
        assertThat(draftLine.count()).isEqualTo(1);
        assertThat(draftLine.totalInsuranceAmountMinor()).isEqualTo(490_000); // 70% of the 700,000 gross
    }

    @Test
    void rejectedClaimsAreVisibleForChasing() {
        UUID admin = bootstrapAdminId();
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        UUID claimId = raiseClaim(admin, 700_000);
        transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claimId, ClaimState.SUBMITTED, null));
        transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claimId, ClaimState.REJECTED, "missing documentation"));

        List<ClaimsSummaryLine> summary = claimsReportService.summarize(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).state()).isEqualTo("REJECTED");
        assertThat(summary.get(0).totalInsuranceAmountMinor()).isEqualTo(490_000); // 70% of the 700,000 gross
    }

    private UUID raiseClaim(UUID admin, long grossMinor) {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID schemeId = insertFixtureSchemeWithVerifiedPolicy(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(grossMinor);
        schemeCoverageRuleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE, BigDecimal.valueOf(70), false, admin));
        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), admin));
        long patientPortion = grossMinor * 30 / 100;
        postPaymentService.recordPayment(new PostPaymentCommand(patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(posted.invoiceLineId(), patientPortion)), admin));
        billingGate.authorizeConsumption(posted.invoiceLineId(), admin);

        return jdbcTemplate.queryForObject("SELECT id FROM insurance_claim WHERE invoice_id = :invoiceId",
                new MapSqlParameterSource("invoiceId", posted.invoiceId()), UUID.class);
    }

    private UUID insertFixtureSchemeWithVerifiedPolicy(UUID patientId) {
        UUID schemeId = jdbcTemplate.queryForObject(
                "INSERT INTO insurance_scheme (code, name) VALUES (:code, 'Test NHIF') RETURNING id",
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
                        .addValue("validFrom", LocalDate.now().minusDays(10)));
        return schemeId;
    }

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
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
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
