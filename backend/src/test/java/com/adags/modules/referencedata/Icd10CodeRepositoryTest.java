package com.adags.modules.referencedata;

import com.adags.modules.referencedata.domain.Icd10Code;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// 10-clinical-reference-data.md §2.1: never deleted, retirement via
// is_active = false, and a re-import must never undo a retirement.
class Icd10CodeRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private Icd10CodeRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE icd10_code CASCADE", new MapSqlParameterSource());
    }

    @Test
    void upsertInsertsANewCodeAndReportsInserted() {
        boolean inserted = repository.upsert(
                new Icd10Code("B50.9", "P. falciparum malaria, unspecified", "I", "B50-B54", null, "2019", true, true, null));

        assertThat(inserted).isTrue();
        Icd10Code saved = repository.findByCode("B50.9").orElseThrow();
        assertThat(saved.title()).isEqualTo("P. falciparum malaria, unspecified");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isBillable()).isTrue();
    }

    @Test
    void upsertOnAnExistingCodeUpdatesFieldsAndReportsNotInserted() {
        repository.upsert(new Icd10Code("B50.9", "Old title", "I", "B50-B54", null, "2016", true, true, null));

        boolean insertedSecondTime = repository.upsert(
                new Icd10Code("B50.9", "New title", "I", "B50-B54", null, "2019", true, true, null));

        assertThat(insertedSecondTime).isFalse();
        Icd10Code saved = repository.findByCode("B50.9").orElseThrow();
        assertThat(saved.title()).isEqualTo("New title");
        assertThat(saved.icd10Release()).isEqualTo("2019");
    }

    // §2.1: "Retirement sets is_active = false" — a later release re-import
    // must not silently undo that clinical-lead decision.
    @Test
    void upsertNeverReactivatesADeactivatedCode() {
        repository.upsert(new Icd10Code("B50.9", "Title", "I", "B50-B54", null, "2016", true, true, null));
        repository.deactivate("B50.9");

        repository.upsert(new Icd10Code("B50.9", "Refreshed title", "I", "B50-B54", null, "2019", true, true, null));

        Icd10Code saved = repository.findByCode("B50.9").orElseThrow();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.title()).isEqualTo("Refreshed title");
    }

    @Test
    void deactivateRetiresRatherThanDeletes() {
        repository.upsert(new Icd10Code("B50.9", "Title", "I", "B50-B54", null, "2019", true, true, null));

        repository.deactivate("B50.9");

        assertThat(repository.findByCode("B50.9")).isPresent();
        assertThat(repository.findByCode("B50.9").orElseThrow().isActive()).isFalse();
    }
}
