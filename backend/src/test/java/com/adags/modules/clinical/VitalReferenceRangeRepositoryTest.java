package com.adags.modules.clinical;

import com.adags.modules.clinical.domain.AgeBand;
import com.adags.modules.clinical.domain.Sex;
import com.adags.modules.clinical.domain.VitalReferenceRange;
import com.adags.modules.clinical.domain.VitalReferenceRangeRepository;
import com.adags.modules.clinical.domain.VitalType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// V9__vital_reference_range.sql's seed data — 06-triage.md §2.2's exact
// adult figures, and the paediatric-bands-with-null-bounds correction.
class VitalReferenceRangeRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private VitalReferenceRangeRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void adultBoundsMatchTheSpecExactly() {
        assertBounds(VitalType.TEMPERATURE, 30.0, 43.0, 35.5, 38.0);
        assertBounds(VitalType.SYSTOLIC_BP, 40.0, 300.0, 90.0, 180.0);
        assertBounds(VitalType.DIASTOLIC_BP, 20.0, 200.0, null, 110.0);
        assertBounds(VitalType.PULSE, 20.0, 250.0, 50.0, 110.0);
        assertBounds(VitalType.RESPIRATORY_RATE, 4.0, 80.0, 12.0, 20.0);
        assertBounds(VitalType.SPO2, 50.0, 100.0, 94.0, null);
    }

    @Test
    void paediatricBandsExistButAreUnconfigured() {
        for (AgeBand band : List.of(AgeBand.NEONATE, AgeBand.INFANT, AgeBand.TODDLER, AgeBand.CHILD, AgeBand.ADOLESCENT)) {
            VitalReferenceRange range = repository.findCurrent(VitalType.PULSE, band, Sex.ANY).orElseThrow();
            assertThat(range.isConfigured()).as("%s should exist but be unconfigured", band).isFalse();
        }
    }

    @Test
    void elderlyBandHasNoRowAtAll() {
        // Not named in "seed adult plus the paediatric bands" — genuinely absent, not merely unconfigured.
        Optional<VitalReferenceRange> range = repository.findCurrent(VitalType.PULSE, AgeBand.ELDERLY, Sex.ANY);
        assertThat(range).isEmpty();
    }

    @Test
    void findUnconfiguredReturnsExactlyTheThirtyPaediatricRows() {
        List<VitalReferenceRange> unconfigured = repository.findUnconfigured();
        assertThat(unconfigured).hasSize(6 * 5); // 6 vitals x 5 paediatric bands
        assertThat(unconfigured).allSatisfy(range -> {
            assertThat(range.ageBand()).isIn(AgeBand.NEONATE, AgeBand.INFANT, AgeBand.TODDLER, AgeBand.CHILD, AgeBand.ADOLESCENT);
            assertThat(range.isConfigured()).isFalse();
        });
    }

    @Test
    void sexSpecificRowTakesPriorityOverAnyWhenBothExist() {
        insertRow(VitalType.TEMPERATURE, AgeBand.ADULT, "FEMALE", 31, 42, 36.0, 37.5);

        VitalReferenceRange resolved = repository.findCurrent(VitalType.TEMPERATURE, AgeBand.ADULT, Sex.FEMALE).orElseThrow();
        assertThat(resolved.sex()).isEqualTo(Sex.FEMALE);
        assertThat(resolved.abnormalLow()).isEqualTo(36.0);

        // A male lookup still falls back to the ANY row from the seed data.
        VitalReferenceRange fallback = repository.findCurrent(VitalType.TEMPERATURE, AgeBand.ADULT, Sex.MALE).orElseThrow();
        assertThat(fallback.sex()).isEqualTo(Sex.ANY);
    }

    // root CLAUDE.md "Concurrency" / "Billing" versioning convention applied
    // to reference data: one current row per (vital, band, sex).
    @Test
    void rejectsASecondCurrentRowForTheSameVitalBandAndSex() {
        assertThatThrownBy(() -> insertRow(VitalType.TEMPERATURE, AgeBand.ADULT, "ANY", 31, 42, 36.0, 37.5))
                .isInstanceOf(DataAccessException.class);
    }

    private void assertBounds(VitalType vitalType, Double implausibleLow, Double implausibleHigh,
                               Double abnormalLow, Double abnormalHigh) {
        VitalReferenceRange range = repository.findCurrent(vitalType, AgeBand.ADULT, Sex.ANY).orElseThrow();
        assertThat(tuple(range.implausibleLow(), range.implausibleHigh(), range.abnormalLow(), range.abnormalHigh()))
                .as(vitalType.name())
                .isEqualTo(tuple(implausibleLow, implausibleHigh, abnormalLow, abnormalHigh));
    }

    private void insertRow(VitalType vitalType, AgeBand ageBand, String sex,
                            double implausibleLow, double implausibleHigh, double abnormalLow, double abnormalHigh) {
        jdbcTemplate.update("""
                        INSERT INTO vital_reference_range
                            (vital_type, age_band, sex, implausible_low, implausible_high, abnormal_low, abnormal_high, created_by)
                        VALUES (:vitalType, :ageBand, :sex, :implausibleLow, :implausibleHigh, :abnormalLow, :abnormalHigh,
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("vitalType", vitalType.name())
                        .addValue("ageBand", ageBand.name())
                        .addValue("sex", sex)
                        .addValue("implausibleLow", implausibleLow)
                        .addValue("implausibleHigh", implausibleHigh)
                        .addValue("abnormalLow", abnormalLow)
                        .addValue("abnormalHigh", abnormalHigh));
    }
}
