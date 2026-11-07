package com.adags.modules.billing;

import com.adags.modules.billing.application.ChargePostingCommand;
import com.adags.modules.billing.application.ChargePostingResult;
import com.adags.modules.billing.application.ChargePostingService;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentBypassSource;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §3: the gate function and its four calling
// rules. Never truncates service_item/price_list/price_list_entry or
// department — backend/CLAUDE.md's TRUNCATE CASCADE lesson.
class BillingGateTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private BillingGate billingGate;

    @Autowired
    private ChargePostingService chargePostingService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Autowired
    private PaymentBypassRepository paymentBypassRepository;

    @Autowired
    private SchemeCoverageRuleRepository schemeCoverageRuleRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE service_authorization, invoice_line, invoice, payment_bypass, outbox_event,
                                 scheme_coverage_rule, insurance_policy, insurance_scheme, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    // A line with nothing owed is trivially satisfied — no payment
    // module, no bypass, no insurance needed.
    @Test
    void aLineWithZeroPatientPortionIsSatisfied() {
        UUID patientId = insertFixturePatient();
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null, bootstrapAdminId()));
        InvoiceLine zeroLine = invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(0, "TZS"), new Money(0, "TZS"), new Money(0, "TZS"), "TestFixture", UUID.randomUUID()));

        assertThat(billingGate.isSatisfied(zeroLine.id())).isTrue();

        // Trivially satisfied by its own immutable data — nothing
        // revocable to protect against, so no authorization is written.
        billingGate.authorizeConsumption(zeroLine.id(), bootstrapAdminId());
        InvoiceLine reloaded = invoiceLineRepository.findById(zeroLine.id()).orElseThrow();
        assertThat(reloaded.authorizationId()).isNull();
    }

    @Test
    void aLineWithNoPaymentNoBypassNoInsuranceIsRejectedWithADestination() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TestFixture", UUID.randomUUID(), bootstrapAdminId()));

        assertThat(billingGate.isSatisfied(posted.invoiceLineId())).isFalse();

        assertThatThrownBy(() -> billingGate.authorizeConsumption(posted.invoiceLineId(), bootstrapAdminId()))
                .isInstanceOf(BillingGateRejectedException.class)
                .satisfies(ex -> {
                    BillingGateRejectedException rejected = (BillingGateRejectedException) ex;
                    assertThat(rejected.outstandingAmount()).isEqualTo(new Money(8_000, "TZS"));
                    assertThat(rejected.serviceName()).isEqualTo("General consultation");
                });
    }

    // The test the user explicitly asked for: grant a bypass, post a
    // line, revoke the bypass, assert the line still satisfies the gate.
    // 02-billing-architecture.md §3.1 clause 4 — durable provenance is
    // the entire point of the fourth OR clause.
    @Test
    void aLineAuthorizedUnderABypassStillSatisfiesTheGateAfterRevocation() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        PaymentBypass bypass = paymentBypassRepository.insert(
                PaymentBypass.grant(encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, "IMMEDIATE triage", bootstrapAdminId()));

        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TestFixture", UUID.randomUUID(), bootstrapAdminId()));

        billingGate.authorizeConsumption(posted.invoiceLineId(), bootstrapAdminId());
        InvoiceLine authorized = invoiceLineRepository.findById(posted.invoiceLineId()).orElseThrow();
        assertThat(authorized.authorizationId()).isNotNull();

        revokeBypass(bypass.id());

        assertThat(billingGate.isSatisfied(posted.invoiceLineId())).isTrue();
        // A second authorizeConsumption call must not throw and must not
        // write a second ServiceAuthorization row — clause 4 short-circuits.
        billingGate.authorizeConsumption(posted.invoiceLineId(), bootstrapAdminId());
        Integer authorizationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM service_authorization WHERE invoice_line_id = :id",
                new MapSqlParameterSource("id", posted.invoiceLineId()), Integer.class);
        assertThat(authorizationCount).isEqualTo(1);
    }

    @Test
    void anActiveBypassSatisfiesALineWithoutAnAuthorizationYet() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        paymentBypassRepository.insert(
                PaymentBypass.grant(encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, "IMMEDIATE triage", bootstrapAdminId()));
        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TestFixture", UUID.randomUUID(), bootstrapAdminId()));

        assertThat(billingGate.isSatisfied(posted.invoiceLineId())).isTrue();
    }

    // Phase 7 item 3 part 3: "gateSatisfied via insurance = VERIFIED AND
    // scheme_covers(service) AND patient_portion_outstanding == 0." FULL
    // coverage means patientPortion is 0 from the moment the line is
    // posted — this must still require the policy to be VERIFIED, not
    // grant for free the moment a coverage rule happens to zero it out.
    @Test
    void fullCoverageWithAVerifiedPolicyIsSatisfiedWithoutAnyPayment() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID schemeId = insertFixtureSchemeWithPolicy(patientId, "VERIFIED");
        String itemCode = insertFixtureNhifPricedServiceItem(500_000);
        schemeCoverageRuleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.CONSULTATION, CoverageType.FULL, null, false, admin));

        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TestFixture", UUID.randomUUID(), admin));

        assertThat(billingGate.isSatisfied(posted.invoiceLineId())).isTrue();
        billingGate.authorizeConsumption(posted.invoiceLineId(), admin);
        InvoiceLine authorized = invoiceLineRepository.findById(posted.invoiceLineId()).orElseThrow();
        assertThat(authorized.authorizationId()).isNotNull();
    }

    // The gap this part closes: an UNVERIFIED policy must not let a
    // FULL-coverage line through for free just because its
    // patientPortion happens to be zero.
    @Test
    void fullCoverageWithAnUnverifiedPolicyIsNotSatisfied() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID schemeId = insertFixtureSchemeWithPolicy(patientId, "UNVERIFIED");
        String itemCode = insertFixtureNhifPricedServiceItem(500_000);
        schemeCoverageRuleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.CONSULTATION, CoverageType.FULL, null, false, admin));

        ChargePostingResult posted = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TestFixture", UUID.randomUUID(), admin));

        assertThat(billingGate.isSatisfied(posted.invoiceLineId())).isFalse();
    }

    private void revokeBypass(UUID bypassId) {
        jdbcTemplate.update("""
                        UPDATE payment_bypass
                        SET status = 'REVOKED', revoked_by = :revokedBy, revoked_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("revokedBy", bootstrapAdminId())
                        .addValue("id", bypassId));
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

    private UUID insertFixtureSchemeWithPolicy(UUID patientId, String verificationStatus) {
        UUID schemeId = jdbcTemplate.queryForObject("""
                        INSERT INTO insurance_scheme (code, name) VALUES (:code, 'Test NHIF') RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO insurance_policy (patient_id, scheme_id, policy_number, valid_from, verification_status)
                        VALUES (:patientId, :schemeId, :policyNumber, :validFrom, :verificationStatus)
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("schemeId", schemeId)
                        .addValue("policyNumber", "POL-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("validFrom", LocalDate.now().minusDays(10))
                        .addValue("verificationStatus", verificationStatus));
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
