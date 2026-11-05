package com.adags.modules.referencedata;

import com.adags.modules.referencedata.application.Icd10ImportService;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import com.adags.modules.referencedata.domain.Icd10SearchRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class WhoIcd10ImportTest extends IntegrationTestSupport {
    @Autowired Icd10ImportService importer;
    @Autowired Icd10CodeRepository codes;
    @Autowired Icd10SearchRepository search;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void fullReleaseIsRepeatableAndPreservesCuratedFlags() throws IOException {
        var first = importer.importWho2019();
        assertThat(first.imported() + first.updated() + first.skipped() + first.rejected()).isEqualTo(11539);
        assertThat(first.rejected()).isZero();
        assertThat(codes.findByCode("B54").orElseThrow().title()).isEqualTo("Unspecified malaria");
        assertThat(codes.findByCode("I10").orElseThrow().isSelectable()).isTrue();
        assertThat(codes.findByCode("A00.0").orElseThrow().isSelectable()).isTrue();
        assertThat(codes.findByCode("A00.0").orElseThrow().isBillable()).isFalse();
        assertThat(codes.findByCode("A00").orElseThrow().isSelectable()).isFalse();
        assertThat(codes.findByCode("D63").orElseThrow().isSelectable()).isFalse();
        assertThat(codes.findByCode("I10").orElseThrow().icd10Release()).isEqualTo(Icd10ImportService.WHO_2019_RELEASE);

        jdbc.update("UPDATE icd10_code SET is_active = FALSE, is_billable = TRUE WHERE code = 'J45.9'", new MapSqlParameterSource());
        var again = importer.importWho2019();
        assertThat(again.imported()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.skipped()).isEqualTo(11539);
        assertThat(codes.findByCode("J45.9").orElseThrow().isActive()).isFalse();
        assertThat(codes.findByCode("J45.9").orElseThrow().isBillable()).isTrue();

        var actor = UUID.randomUUID();
        for (String term : new String[]{"Malaria", "Typhoid", "Hypertension", "Diabetes", "Asthma", "Pneumonia", "Syphilis", "Salmonella", "salmonera", "B54"}) {
            assertThat(search.search(term, null, actor, 20)).as(term).isNotEmpty();
        }
    }
}
