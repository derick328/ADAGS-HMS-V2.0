package com.adags.modules.insurance;

import com.adags.modules.insurance.domain.InsuranceScheme;
import com.adags.modules.insurance.domain.InsuranceSchemeRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V6's insurance_scheme stub becomes real (Phase 7 item 3 part 1).
class InsuranceSchemeRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private InsuranceSchemeRepository schemeRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE insurance_scheme CASCADE", new MapSqlParameterSource());
    }

    @Test
    void insertAndFindRoundTripTheNewFields() {
        InsuranceScheme scheme = schemeRepository.insert(InsuranceScheme.create(
                "TEST-NHIF-" + UUID.randomUUID().toString().substring(0, 8), "Test NHIF",
                "PORTAL", "claims@nhif.example", 60));

        InsuranceScheme found = schemeRepository.findById(scheme.id()).orElseThrow();

        assertThat(found.active()).isTrue();
        assertThat(found.claimSubmissionMethod()).isEqualTo("PORTAL");
        assertThat(found.contact()).isEqualTo("claims@nhif.example");
        assertThat(found.paymentTermsDays()).isEqualTo(60);
    }

    @Test
    void findByCodeResolvesTheSameScheme() {
        String code = "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8);
        InsuranceScheme scheme = schemeRepository.insert(InsuranceScheme.create(code, "Test Scheme", null, null, null));

        assertThat(schemeRepository.findByCode(code).orElseThrow().id()).isEqualTo(scheme.id());
    }

    @Test
    void findAllActiveExcludesInactiveSchemes() {
        String code = "TEST-INACTIVE-" + UUID.randomUUID().toString().substring(0, 8);
        InsuranceScheme scheme = schemeRepository.insert(InsuranceScheme.create(code, "Inactive Scheme", null, null, null));
        jdbcTemplate.update("UPDATE insurance_scheme SET active = FALSE WHERE id = :id",
                new MapSqlParameterSource("id", scheme.id()));

        assertThat(schemeRepository.findAllActive()).noneMatch(s -> s.id().equals(scheme.id()));
    }

    @Test
    void codeIsUnique() {
        String code = "TEST-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        schemeRepository.insert(InsuranceScheme.create(code, "First", null, null, null));

        assertThatThrownBy(() -> schemeRepository.insert(InsuranceScheme.create(code, "Second", null, null, null)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void createRejectsABlankCode() {
        assertThatThrownBy(() -> InsuranceScheme.create("  ", "Name", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsANonPositivePaymentTerm() {
        assertThatThrownBy(() -> InsuranceScheme.create("CODE", "Name", null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
