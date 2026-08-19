package com.adags.modules.insurance;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException;
import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.InsuranceClaim;
import com.adags.modules.insurance.domain.InsuranceClaimRepository;
import com.adags.modules.insurance.domain.InsuranceScheme;
import com.adags.modules.insurance.domain.InsuranceSchemeRepository;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.shared.kernel.CoverageSplit;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 7 item 3 part 2: the facade seam ChargePostingService will call.
class InsuranceFacadeImplTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private InsuranceFacade insuranceFacade;
    @Autowired
    private InsuranceSchemeRepository schemeRepository;
    @Autowired
    private SchemeCoverageRuleRepository ruleRepository;
    @Autowired
    private InsuranceClaimRepository claimRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE insurance_claim, scheme_coverage_rule, insurance_scheme, invoice, encounter,
                                 patient, outbox_event CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void resolvesTheApplicableRuleAndComputesTheSplit() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();
        SchemeCoverageRule rule = ruleRepository.insert(SchemeCoverageRule.forServiceItem(
                schemeId, serviceItemId, CoverageType.PERCENTAGE, BigDecimal.valueOf(80), false, admin));

        CoverageSplit split = insuranceFacade.resolveCoverageSplit(
                schemeId, serviceItemId, ServiceCategory.LAB.name(), new Money(10_000, "TZS"), LocalDate.now());

        assertThat(split.insurancePortion()).isEqualTo(new Money(8_000, "TZS"));
        assertThat(split.patientPortion()).isEqualTo(new Money(2_000, "TZS"));
        assertThat(split.coverageRuleId()).isEqualTo(rule.id());
    }

    // root CLAUDE.md "Billing"-equivalent discipline: "No rule found... do
    // not default to EXCLUDED or FULL. Throw."
    @Test
    void throwsRatherThanGuessingWhenNoRuleCoversTheScope() {
        UUID schemeId = insertFixtureScheme();

        assertThatThrownBy(() -> insuranceFacade.resolveCoverageSplit(
                schemeId, UUID.randomUUID(), ServiceCategory.PROCEDURE.name(), new Money(10_000, "TZS"), LocalDate.now()))
                .isInstanceOf(CoverageRuleNotConfiguredException.class);
    }

    @Test
    void isCoveredIsTrueForAnythingButExcluded() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();
        ruleRepository.insert(SchemeCoverageRule.forServiceItem(
                schemeId, serviceItemId, CoverageType.PERCENTAGE, BigDecimal.valueOf(70), false, admin));

        Optional<UUID> coveringRuleId = insuranceFacade.resolveCoveringRuleId(
                schemeId, serviceItemId, ServiceCategory.LAB.name(), LocalDate.now());
        assertThat(coveringRuleId).isPresent();
    }

    @Test
    void isCoveredIsFalseForExcluded() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();
        ruleRepository.insert(SchemeCoverageRule.forServiceItem(
                schemeId, serviceItemId, CoverageType.EXCLUDED, null, false, admin));

        assertThat(insuranceFacade.resolveCoveringRuleId(schemeId, serviceItemId, ServiceCategory.LAB.name(), LocalDate.now()))
                .isEmpty();
    }

    @Test
    void raiseClaimAllocatesANumberAndPublishesClaimRaised() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();
        SchemeCoverageRule rule = ruleRepository.insert(SchemeCoverageRule.forServiceItem(
                schemeId, serviceItemId, CoverageType.FULL, null, false, admin));
        UUID invoiceId = insertFixtureInvoice(admin);

        UUID claimId = insuranceFacade.raiseClaim(invoiceId, BOOTSTRAP_FACILITY_ID, rule.id(), new Money(700_000, "TZS"), admin);

        InsuranceClaim claim = claimRepository.findById(claimId).orElseThrow();
        assertThat(claim.state()).isEqualTo(ClaimState.DRAFT);
        assertThat(claim.claimNo()).isNotBlank();
        assertThat(claim.schemeId()).isEqualTo(schemeId);
        assertThat(claim.invoiceId()).isEqualTo(invoiceId);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'ClaimRaised' AND payload->>'claimId' = :id",
                new MapSqlParameterSource("id", claimId.toString()), Integer.class);
        assertThat(eventCount).isEqualTo(1);
    }

    private UUID insertFixtureInvoice(UUID admin) {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
        Invoice invoice = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null, admin));
        return invoice.id();
    }

    private UUID insertFixtureScheme() {
        InsuranceScheme scheme = schemeRepository.insert(InsuranceScheme.create(
                "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8), "Test Scheme", null, null, null));
        return scheme.id();
    }

    private UUID insertFixtureServiceItem() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO service_item (code, name, category)
                        VALUES (:code, 'Test Lab Item', 'LAB')
                        RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-ITEM-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
