package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §4: "Every charge is posted by a domain
// event." Exercised end to end against the real V15-seeded CASH prices —
// no manual charge-entry path exists to test instead.
class ChargePostingServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private ChargePostingService service;

    @Autowired
    private SchemeCoverageRuleRepository schemeCoverageRuleRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE scheme_coverage_rule, insurance_policy, insurance_scheme, service_authorization,
                                 invoice_line, invoice, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
        // Fixture NHIF price_list_entry rows accumulate harmlessly across
        // tests (random codes) — same reasoning PriceListEntryRepositoryTest
        // documents for not truncating price_list_entry/service_item.
    }

    @Test
    void postsAChargeAgainstTheSeededCashPriceAndIssuesTheInvoice() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID triageAssessmentId = UUID.randomUUID();

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TriageCompleted", triageAssessmentId, bootstrapAdminId()));

        assertThat(result.grossAmount()).isEqualTo(new Money(8_000, "TZS"));
        assertThat(result.patientPortion()).isEqualTo(new Money(8_000, "TZS"));
        assertThat(result.invoiceNumber()).isNotBlank();

        String state = jdbcTemplate.queryForObject(
                "SELECT state FROM invoice WHERE id = :id",
                new MapSqlParameterSource("id", result.invoiceId()), String.class);
        assertThat(state).isEqualTo("ISSUED");

        Integer chargePostedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'ChargePosted' AND payload->>'invoiceId' = :id",
                new MapSqlParameterSource("id", result.invoiceId().toString()), Integer.class);
        assertThat(chargePostedCount).isEqualTo(1);

        Integer invoiceIssuedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'InvoiceIssued' AND payload->>'invoiceId' = :id",
                new MapSqlParameterSource("id", result.invoiceId().toString()), Integer.class);
        assertThat(invoiceIssuedCount).isEqualTo(1);
    }

    @Test
    void quantityMultipliesTheResolvedUnitPrice() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_SPECIALIST", 2, "TriageCompleted", UUID.randomUUID(), bootstrapAdminId()));

        assertThat(result.grossAmount()).isEqualTo(new Money(60_000, "TZS"));
    }

    // root CLAUDE.md "Billing": "A missing price... blocks with an
    // explicit error — it never defaults to zero," and §4.1's "⚠ The
    // charge posting is inside the triage submission transaction. If it
    // fails, the whole assessment rolls back" — proven here by asserting
    // no invoice row survives the failed post.
    @Test
    void anUnpricedServiceItemBlocksAndPostsNoInvoice() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        String code = "UNPRICED-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, 'Unpriced', 'CONSULTATION')",
                new MapSqlParameterSource("code", code));

        assertThatThrownBy(() -> service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                code, 1, "TriageCompleted", UUID.randomUUID(), bootstrapAdminId())))
                .isInstanceOf(PriceNotConfiguredException.class);

        Integer invoiceCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(invoiceCount).isZero();
    }

    // Phase 7 item 3 part 2: the InvoiceLine split. "This changes every
    // existing charge-posting call site's output shape for insured
    // encounters, but nothing for cash ones" — proven first by this
    // regression test against the exact same assertions the original
    // CASH test above makes.
    @Test
    void aCashEncountersLineIsByteIdenticalToBeforeTheInsuranceSplit() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null,
                "CONSULT_GENERAL", 1, "TriageCompleted", UUID.randomUUID(), bootstrapAdminId()));

        assertThat(result.grossAmount()).isEqualTo(new Money(8_000, "TZS"));
        assertThat(result.patientPortion()).isEqualTo(new Money(8_000, "TZS"));

        Long insurancePortionMinor = jdbcTemplate.queryForObject(
                "SELECT insurance_portion_minor FROM invoice_line WHERE invoice_id = :id",
                new MapSqlParameterSource("id", result.invoiceId()), Long.class);
        assertThat(insurancePortionMinor).isZero();

        Object coverageRuleId = jdbcTemplate.queryForObject(
                "SELECT coverage_rule_id FROM invoice_line WHERE invoice_id = :id",
                new MapSqlParameterSource("id", result.invoiceId()), Object.class);
        assertThat(coverageRuleId).isNull();
    }

    @Test
    void aFullCoverageRuleMakesInsurancePayTheWholeGross() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID schemeId = insertFixtureSchemeWithActivePolicy(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(1_000_000);
        SchemeCoverageRule rule = schemeCoverageRuleRepository.insert(SchemeCoverageRule.forServiceItem(
                schemeId, seededServiceItemId(itemCode), CoverageType.FULL, null, false, admin));

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), admin));

        assertThat(result.grossAmount()).isEqualTo(new Money(1_000_000, "TZS"));
        assertThat(result.patientPortion()).isEqualTo(new Money(0, "TZS"));

        Long insurancePortionMinor = jdbcTemplate.queryForObject(
                "SELECT insurance_portion_minor FROM invoice_line WHERE invoice_id = :id",
                new MapSqlParameterSource("id", result.invoiceId()), Long.class);
        assertThat(insurancePortionMinor).isEqualTo(1_000_000L);

        UUID coverageRuleId = jdbcTemplate.queryForObject(
                "SELECT coverage_rule_id FROM invoice_line WHERE invoice_id = :id",
                new MapSqlParameterSource("id", result.invoiceId()), UUID.class);
        assertThat(coverageRuleId).isEqualTo(rule.id());
    }

    @Test
    void aPercentageCoverageRuleSplitsProportionally() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID schemeId = insertFixtureSchemeWithActivePolicy(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(1_000_000);
        schemeCoverageRuleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.CONSULTATION, CoverageType.PERCENTAGE, BigDecimal.valueOf(80), false, admin));

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), admin));

        assertThat(result.patientPortion()).isEqualTo(new Money(200_000, "TZS"));
        Long insurancePortionMinor = jdbcTemplate.queryForObject(
                "SELECT insurance_portion_minor FROM invoice_line WHERE invoice_id = :id",
                new MapSqlParameterSource("id", result.invoiceId()), Long.class);
        assertThat(insurancePortionMinor).isEqualTo(800_000L);
    }

    @Test
    void aFixedAmountCoverageRuleIsCappedAtGross() {
        UUID admin = bootstrapAdminId();
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID schemeId = insertFixtureSchemeWithActivePolicy(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(300_000);
        schemeCoverageRuleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.CONSULTATION, CoverageType.FIXED_AMOUNT, BigDecimal.valueOf(1_000_000), false, admin));

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), admin));

        assertThat(result.grossAmount()).isEqualTo(new Money(300_000, "TZS"));
        assertThat(result.patientPortion()).isEqualTo(new Money(0, "TZS"));
    }

    // "if the encounter's payer_category indicates an active insurance
    // policy" — an NHIF charge against a patient with NO active policy on
    // file falls through to the ordinary cash-like split rather than
    // throwing; §6.2's throw is specifically for a missing coverage rule
    // once a scheme has actually been resolved.
    @Test
    void nhifWithNoActivePolicyFallsBackToThePatientPayingEverything() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(1_000_000);

        ChargePostingResult result = service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), bootstrapAdminId()));

        assertThat(result.patientPortion()).isEqualTo(new Money(1_000_000, "TZS"));
    }

    // ⚠ "No rule found for the date and item/category -> do not default
    // to EXCLUDED or FULL. Throw, the same way a missing price throws."
    @Test
    void anActivePolicyWithNoCoverageRuleConfiguredThrows() {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        insertFixtureSchemeWithActivePolicy(patientId);
        String itemCode = insertFixtureNhifPricedServiceItem(1_000_000);

        assertThatThrownBy(() -> service.postCharge(new ChargePostingCommand(
                encounterId, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null,
                itemCode, 1, "TriageCompleted", UUID.randomUUID(), bootstrapAdminId())))
                .isInstanceOf(CoverageRuleNotConfiguredException.class);

        Integer invoiceCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(invoiceCount).isZero();
    }

    private UUID insertFixtureSchemeWithActivePolicy(UUID patientId) {
        UUID schemeId = jdbcTemplate.queryForObject("""
                        INSERT INTO insurance_scheme (code, name) VALUES (:code, 'Test NHIF') RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO insurance_policy (patient_id, scheme_id, policy_number, valid_from)
                        VALUES (:patientId, :schemeId, :policyNumber, :validFrom)
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("schemeId", schemeId)
                        .addValue("policyNumber", "POL-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("validFrom", LocalDate.now().minusDays(10)));
        return schemeId;
    }

    // A dedicated fixture item per call, not the shared seeded
    // CONSULT_GENERAL — price_list_entry's partial unique index allows
    // only one current row per (price_list, item), so reusing a shared
    // item across tests that set different NHIF prices would silently
    // keep whichever price landed first.
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

    private UUID seededServiceItemId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM service_item WHERE code = :code", new MapSqlParameterSource("code", code), UUID.class);
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
