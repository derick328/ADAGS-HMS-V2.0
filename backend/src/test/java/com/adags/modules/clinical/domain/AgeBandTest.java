package com.adags.modules.clinical.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 06-triage.md §2.2: "neonate 0-1m, infant 1-12m, toddler 1-3y, child
// 3-12y, adolescent 12-18y, adult, elderly 65+." Boundaries matter clinically
// — a neonate misclassified as INFANT is measured against the wrong bounds.
class AgeBandTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 6, 15);

    @Test
    void neonateIsUnderOneMonth() {
        assertThat(AgeBand.forAge(ASOF, ASOF)).isEqualTo(AgeBand.NEONATE);
        assertThat(AgeBand.forAge(ASOF.minusDays(29), ASOF)).isEqualTo(AgeBand.NEONATE);
    }

    @Test
    void infantIsOneToTwelveMonths() {
        assertThat(AgeBand.forAge(ASOF.minusMonths(1), ASOF)).isEqualTo(AgeBand.INFANT);
        assertThat(AgeBand.forAge(ASOF.minusMonths(11), ASOF)).isEqualTo(AgeBand.INFANT);
    }

    @Test
    void toddlerIsOneToThreeYears() {
        assertThat(AgeBand.forAge(ASOF.minusMonths(12), ASOF)).isEqualTo(AgeBand.TODDLER);
        assertThat(AgeBand.forAge(ASOF.minusYears(2), ASOF)).isEqualTo(AgeBand.TODDLER);
    }

    @Test
    void childIsThreeToTwelveYears() {
        assertThat(AgeBand.forAge(ASOF.minusYears(3), ASOF)).isEqualTo(AgeBand.CHILD);
        assertThat(AgeBand.forAge(ASOF.minusYears(11), ASOF)).isEqualTo(AgeBand.CHILD);
    }

    @Test
    void adolescentIsTwelveToEighteenYears() {
        assertThat(AgeBand.forAge(ASOF.minusYears(12), ASOF)).isEqualTo(AgeBand.ADOLESCENT);
        assertThat(AgeBand.forAge(ASOF.minusYears(17), ASOF)).isEqualTo(AgeBand.ADOLESCENT);
    }

    @Test
    void adultIsEighteenToSixtyFiveYears() {
        assertThat(AgeBand.forAge(ASOF.minusYears(18), ASOF)).isEqualTo(AgeBand.ADULT);
        assertThat(AgeBand.forAge(ASOF.minusYears(64), ASOF)).isEqualTo(AgeBand.ADULT);
    }

    @Test
    void elderlyIsSixtyFivePlus() {
        assertThat(AgeBand.forAge(ASOF.minusYears(65), ASOF)).isEqualTo(AgeBand.ELDERLY);
        assertThat(AgeBand.forAge(ASOF.minusYears(90), ASOF)).isEqualTo(AgeBand.ELDERLY);
    }
}
