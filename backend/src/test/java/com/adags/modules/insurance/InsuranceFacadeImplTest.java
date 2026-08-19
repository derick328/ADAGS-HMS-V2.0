package com.adags.modules.insurance;

import com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException;
import com.adags.modules.insurance.domain.CoverageType;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 7 item 3 part 2: the facade seam ChargePostingService will call.
class InsuranceFacadeImplTest extends IntegrationTestSupport {

    @Autowired
    private InsuranceFacade insuranceFacade;
    @Autowired
    private InsuranceSchemeRepository schemeRepository;
    @Autowired
    private SchemeCoverageRuleRepository ruleRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE scheme_coverage_rule, insurance_scheme CASCADE", new MapSqlParameterSource());
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
