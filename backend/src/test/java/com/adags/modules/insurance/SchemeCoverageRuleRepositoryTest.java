package com.adags.modules.insurance;

import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.InsuranceScheme;
import com.adags.modules.insurance.domain.InsuranceSchemeRepository;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §6.2: versioned rules, resolved as of the
// SERVICE date — never "today's" rule — with an item-specific rule
// winning over a category rule for the same date.
class SchemeCoverageRuleRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private SchemeCoverageRuleRepository ruleRepository;
    @Autowired
    private InsuranceSchemeRepository schemeRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE scheme_coverage_rule, insurance_scheme CASCADE", new MapSqlParameterSource());
    }

    @Test
    void anItemSpecificRuleWinsOverACategoryRuleForTheSameDate() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();
        ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.PERCENTAGE, BigDecimal.valueOf(80), false, admin));
        ruleRepository.insert(SchemeCoverageRule.forServiceItem(
                schemeId, serviceItemId, CoverageType.FULL, null, true, admin));

        SchemeCoverageRule applicable = ruleRepository.findApplicable(
                schemeId, serviceItemId, ServiceCategory.LAB, LocalDate.now()).orElseThrow();

        assertThat(applicable.serviceItemId()).isEqualTo(serviceItemId);
        assertThat(applicable.coverageType()).isEqualTo(CoverageType.FULL);
    }

    @Test
    void fallsBackToTheCategoryRuleWhenNoItemSpecificRuleExists() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();
        ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.PERCENTAGE, BigDecimal.valueOf(80), false, admin));

        SchemeCoverageRule applicable = ruleRepository.findApplicable(
                schemeId, serviceItemId, ServiceCategory.LAB, LocalDate.now()).orElseThrow();

        assertThat(applicable.serviceCategory()).isEqualTo(ServiceCategory.LAB);
        assertThat(applicable.coverageValue()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    void resolvesAgainstTheRuleThatAppliedOnTheServiceDateNotToday() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();
        LocalDate septemberServiceDate = LocalDate.of(2026, 9, 10);

        SchemeCoverageRule septemberRule = ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.PERCENTAGE, BigDecimal.valueOf(70), false, admin));
        jdbcTemplate.update("""
                        UPDATE scheme_coverage_rule SET effective_from = DATE '2026-01-01', effective_to = DATE '2026-10-31'
                        WHERE id = :id
                        """,
                new MapSqlParameterSource("id", septemberRule.id()));

        SchemeCoverageRule novemberRule = ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.PERCENTAGE, BigDecimal.valueOf(90), false, admin));
        jdbcTemplate.update("UPDATE scheme_coverage_rule SET effective_from = DATE '2026-11-01' WHERE id = :id",
                new MapSqlParameterSource("id", novemberRule.id()));

        // A claim assembled in November for a September service resolves
        // September's 70%, not November's 90%.
        SchemeCoverageRule applicable = ruleRepository.findApplicable(
                schemeId, serviceItemId, ServiceCategory.LAB, septemberServiceDate).orElseThrow();
        assertThat(applicable.id()).isEqualTo(septemberRule.id());
        assertThat(applicable.coverageValue()).isEqualByComparingTo(BigDecimal.valueOf(70));

        SchemeCoverageRule currentlyApplicable = ruleRepository.findApplicable(
                schemeId, serviceItemId, ServiceCategory.LAB, LocalDate.of(2026, 11, 15)).orElseThrow();
        assertThat(currentlyApplicable.id()).isEqualTo(novemberRule.id());
    }

    @Test
    void isEmptyWhenNoRuleCoversTheScopeOrDate() {
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();

        assertThat(ruleRepository.findApplicable(schemeId, serviceItemId, ServiceCategory.PROCEDURE, LocalDate.now()))
                .isEmpty();
    }

    // The DB CHECK behind "exactly one of service_category/service_item_id" —
    // independent of the domain factory methods, which can't be bypassed
    // by a caller writing SQL directly.
    @Test
    void theDatabaseRejectsARuleWithBothCategoryAndItemSet() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        UUID serviceItemId = insertFixtureServiceItem();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO scheme_coverage_rule (scheme_id, service_category, service_item_id, coverage_type, created_by)
                        VALUES (:schemeId, 'LAB', :serviceItemId, 'FULL', :admin)
                        """,
                new MapSqlParameterSource()
                        .addValue("schemeId", schemeId).addValue("serviceItemId", serviceItemId).addValue("admin", admin)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theDatabaseRejectsARuleWithNeitherCategoryNorItemSet() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO scheme_coverage_rule (scheme_id, coverage_type, created_by)
                        VALUES (:schemeId, 'FULL', :admin)
                        """,
                new MapSqlParameterSource().addValue("schemeId", schemeId).addValue("admin", admin)))
                .isInstanceOf(DataAccessException.class);
    }

    // The partial unique index — real protection behind "never edited in
    // place," independent of application code remembering to close the
    // old row first (same pattern as PriceListEntryRepositoryTest).
    @Test
    void aSecondCurrentRuleForTheSameCategoryScopeIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.FULL, null, false, admin));

        assertThatThrownBy(() -> ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.EXCLUDED, null, false, admin)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void closingARuleThenInsertingAReplacementIsAccepted() {
        UUID admin = bootstrapAdminId();
        UUID schemeId = insertFixtureScheme();
        SchemeCoverageRule original = ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.PERCENTAGE, BigDecimal.valueOf(80), false, admin));

        ruleRepository.close(original.id(), LocalDate.now());
        SchemeCoverageRule replacement = ruleRepository.insert(SchemeCoverageRule.forCategory(
                schemeId, ServiceCategory.LAB, CoverageType.PERCENTAGE, BigDecimal.valueOf(90), false, admin));

        assertThat(replacement.id()).isNotEqualTo(original.id());
        Integer closedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM scheme_coverage_rule WHERE id = :id AND effective_to IS NOT NULL",
                new MapSqlParameterSource("id", original.id()), Integer.class);
        assertThat(closedCount).isEqualTo(1);
    }

    private UUID insertFixtureScheme() {
        InsuranceScheme scheme = schemeRepository.insert(InsuranceScheme.create(
                "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8), "Test Scheme", "PORTAL", "test@scheme.example", 30));
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
