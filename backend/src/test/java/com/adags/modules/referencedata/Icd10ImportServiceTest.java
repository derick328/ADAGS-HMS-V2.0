package com.adags.modules.referencedata;

import com.adags.modules.referencedata.application.Icd10ImportResult;
import com.adags.modules.referencedata.application.Icd10ImportRow;
import com.adags.modules.referencedata.application.Icd10ImportService;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 10-clinical-reference-data.md §4.1: "Import the full WHO ICD-10 release."
class Icd10ImportServiceTest extends IntegrationTestSupport {

    @Autowired
    private Icd10ImportService importService;

    @Autowired
    private Icd10CodeRepository codeRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE icd10_code CASCADE", new MapSqlParameterSource());
    }

    @Test
    void importingNewCodesInsertsThemAll() {
        Icd10ImportResult result = importService.importRelease("2019", List.of(
                new Icd10ImportRow("B50.9", "P. falciparum malaria, unspecified", "I", "B50-B54", null, true),
                new Icd10ImportRow("B54", "Unspecified malaria", "I", "B50-B54", null, true)));

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(codeRepository.findByCode("B50.9")).isPresent();
        assertThat(codeRepository.findByCode("B54")).isPresent();
    }

    @Test
    void reImportingUpdatesExistingCodesAndReportsUpdatedCount() {
        importService.importRelease("2016", List.of(
                new Icd10ImportRow("B50.9", "Old title", "I", "B50-B54", null, true)));

        Icd10ImportResult result = importService.importRelease("2019", List.of(
                new Icd10ImportRow("B50.9", "New title", "I", "B50-B54", null, true)));

        assertThat(result.inserted()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(codeRepository.findByCode("B50.9").orElseThrow().title()).isEqualTo("New title");
    }

    @Test
    void reImportNeverReactivatesACodeTheClinicalLeadRetired() {
        importService.importRelease("2016", List.of(
                new Icd10ImportRow("B50.9", "Title", "I", "B50-B54", null, true)));
        codeRepository.deactivate("B50.9");

        importService.importRelease("2019", List.of(
                new Icd10ImportRow("B50.9", "Refreshed title", "I", "B50-B54", null, true)));

        assertThat(codeRepository.findByCode("B50.9").orElseThrow().isActive()).isFalse();
    }

    // icd10_code.parent_code's FK is DEFERRABLE INITIALLY DEFERRED (V13)
    // specifically so a batch doesn't have to be pre-sorted parent-first —
    // this is the case that proves it: the child is listed before the
    // parent, and the whole batch still succeeds because both land in the
    // same transaction and the FK is only checked at commit.
    @Test
    void aChildCodeListedBeforeItsParentInTheSameBatchStillImports() {
        Icd10ImportResult result = importService.importRelease("2019", List.of(
                new Icd10ImportRow("B50.9", "P. falciparum malaria, unspecified", "I", "B50-B54", "B50", true),
                new Icd10ImportRow("B50", "Plasmodium falciparum malaria", "I", "B50-B54", null, false)));

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(codeRepository.findByCode("B50.9").orElseThrow().parentCode()).isEqualTo("B50");
    }

    @Test
    void aBlankReleaseIsRejected() {
        assertThatThrownBy(() -> importService.importRelease("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
