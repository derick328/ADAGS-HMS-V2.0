package com.adags.modules.laboratory.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LabReferenceRangeResolverTest {

    private static final UUID PARAMETER_ID = UUID.randomUUID();

    @Test
    void ageInMonthsMatchesPeriodBetweenDobAndAsOf() {
        int months = LabReferenceRangeResolver.ageInMonths(LocalDate.of(2024, 1, 15), LocalDate.of(2026, 3, 15));
        assertThat(months).isEqualTo(26);
    }

    @Test
    void anExactSexRowWinsOverAnyAtTheSameAgeBand() {
        LabReferenceRange anyRange = range(0, null, Sex.ANY);
        LabReferenceRange maleRange = range(0, null, Sex.MALE);

        LabReferenceRange resolved = LabReferenceRangeResolver
                .resolve(List.of(anyRange, maleRange), 300, Sex.MALE).orElseThrow();

        assertThat(resolved).isEqualTo(maleRange);
    }

    @Test
    void anyIsUsedWhenNoSexSpecificRowExists() {
        LabReferenceRange anyRange = range(0, null, Sex.ANY);

        LabReferenceRange resolved = LabReferenceRangeResolver.resolve(List.of(anyRange), 300, Sex.FEMALE).orElseThrow();

        assertThat(resolved).isEqualTo(anyRange);
    }

    @Test
    void aRowForTheWrongSexIsNeverSelected() {
        LabReferenceRange maleRange = range(0, null, Sex.MALE);

        assertThat(LabReferenceRangeResolver.resolve(List.of(maleRange), 300, Sex.FEMALE)).isEmpty();
    }

    @Test
    void anAgeOutsideEveryBandResolvesToNothing() {
        LabReferenceRange paediatric = range(0, 216, Sex.ANY);

        assertThat(LabReferenceRangeResolver.resolve(List.of(paediatric), 300, Sex.ANY)).isEmpty();
    }

    @Test
    void anUnboundedUpperAgeMaxCoversAnyOlderAge() {
        LabReferenceRange adult = range(216, null, Sex.ANY);

        assertThat(LabReferenceRangeResolver.resolve(List.of(adult), 900, Sex.ANY)).isPresent();
    }

    private LabReferenceRange range(int ageMin, Integer ageMax, Sex sex) {
        return new LabReferenceRange(UUID.randomUUID(), PARAMETER_ID, ageMin, ageMax, sex,
                0.0, 15.0, 3.5, 5.0, 2.5, 6.5, LocalDate.now(), null, UUID.randomUUID(), null);
    }
}
