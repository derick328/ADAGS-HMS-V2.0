package com.adags.modules.reporting;

import com.adags.modules.billing.application.ChargePostingCommand;
import com.adags.modules.billing.application.ChargePostingResult;
import com.adags.modules.billing.application.ChargePostingService;
import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentBypassSource;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.modules.reporting.application.RevenueReport;
import com.adags.modules.reporting.application.RevenueReportService;
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

// 02-billing-architecture.md §13: "Three corrections that determine
// whether the numbers mean anything." Never truncates
// service_item/price_list/price_list_entry or department — backend/
// CLAUDE.md's TRUNCATE CASCADE lesson.
class RevenueReportServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RevenueReportService revenueReportService;
    @Autowired
    private ChargePostingService chargePostingService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private BillingGate billingGate;
    @Autowired
    private PaymentBypassRepository paymentBypassRepository;
    @Autowired
    private SchemeCoverageRuleRepository schemeCoverageRuleRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE insurance_claim, scheme_coverage_rule, insurance_policy, insurance_scheme,
                                 payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, payment_bypass, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void collectedCashSumsSettledPaymentsInThePeriod() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId, BOOTSTRAP_FACILITY_ID);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult posted = postConsultCharge(encounterId, patientId, admin);
        postPaymentService.recordPayment(new PostPaymentCommand(patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(posted.invoiceLineId(), 8_000)), admin));

        RevenueReport report = revenueReportService.generate(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(report.collectedCashMinor()).isEqualTo(8_000);
        assertThat(report.insuranceBilledMinor()).isEqualTo(0);
    }

    @Test
    void paymentsOutsideThePeriodAreExcluded() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId, BOOTSTRAP_FACILITY_ID);
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        ChargePostingResult posted = postConsultCharge(encounterId, patientId, admin);
        postPaymentService.recordPayment(new PostPaymentCommand(patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(posted.invoiceLineId(), 8_000)), admin));
        jdbcTemplate.update("UPDATE payment SET received_at = now() - interval '10 days'", new MapSqlParameterSource());

        RevenueReport report = revenueReportService.generate(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(report.collectedCashMinor()).isEqualTo(0);
    }

    @Test
    void paymentsAtAnotherFacilityAreExcluded() {
        UUID admin = bootstrapAdminId();
        UUID otherFacilityId = jdbcTemplate.queryForObject(
                "INSERT INTO facility (code, name) VALUES (:code, 'Other Facility') RETURNING id",
                new MapSqlParameterSource("code", "OTHER-" + UUID.randomUUID().toString().substring(0, 8)), UUID.class);
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId, otherFacilityId);
        openCashierShiftService.open(otherFacilityId, admin);
        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, otherFacilityId, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TestFixture", UUID.randomUUID(), admin));
        postPaymentService.recordPayment(new PostPaymentCommand(patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(posted.invoiceLineId(), 8_000)), admin));

        RevenueReport report = revenueReportService.generate(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(report.collectedCashMinor()).isEqualTo(0);
    }

    // §13: "Claims are billed, not collected" — insuranceBilled reflects
    // the claim being raised, not any later remittance, and is a
    // deliberately separate figure from collectedCash.
    @Test
    void insuranceBilledSumsInsurancePortionOfInvoicesWithARaisedClaim() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId, BOOTSTRAP_FACILITY_ID);
        UUID schemeId = insertFixtureSchemeWithVerifiedPolicy(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(1_000_000);
        schemeCoverageRuleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE, BigDecimal.valueOf(70), false, admin));
        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), admin));
        openCashierShiftService.open(BOOTSTRAP_FACILITY_ID, admin);
        postPaymentService.recordPayment(new PostPaymentCommand(patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(posted.invoiceLineId(), 300_000)), admin));
        billingGate.authorizeConsumption(posted.invoiceLineId(), admin);

        RevenueReport report = revenueReportService.generate(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(report.collectedCashMinor()).isEqualTo(300_000);
        assertThat(report.insuranceBilledMinor()).isEqualTo(700_000);
    }

    // "Bypass debt is neither [revenue nor collected] — accrued and
    // unpaid, a receivable." A snapshot figure, unbounded by the period.
    @Test
    void emergencyBypassDebtSumsUnsettledBypassAuthorizedLines() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId, BOOTSTRAP_FACILITY_ID);
        paymentBypassRepository.insert(
                PaymentBypass.grant(encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, "IMMEDIATE triage", admin));
        ChargePostingResult posted = postConsultCharge(encounterId, patientId, admin);
        billingGate.authorizeConsumption(posted.invoiceLineId(), admin);

        RevenueReport report = revenueReportService.generate(
                LocalDate.now().minusYears(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(report.emergencyBypassDebtMinor()).isEqualTo(8_000);
    }

    @Test
    void depositLiabilityIsAlwaysZeroBecauseNoDepositLedgerExists() {
        RevenueReport report = revenueReportService.generate(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), BOOTSTRAP_FACILITY_ID);

        assertThat(report.depositLiabilityMinor()).isEqualTo(0);
    }

    private ChargePostingResult postConsultCharge(UUID encounterId, UUID patientId, UUID admin) {
        return chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TriageCompleted", UUID.randomUUID(), admin));
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

    private UUID insertFixtureEncounter(UUID patientId, UUID facilityId) {
        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", facilityId)
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
                        .addValue("facilityId", facilityId)
                        .addValue("departmentId", departmentId),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
