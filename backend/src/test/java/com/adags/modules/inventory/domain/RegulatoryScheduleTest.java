package com.adags.modules.inventory.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// D9: only CONTROLLED blocks — UNCLASSIFIED and every other schedule are
// dispensable, a deliberate departure from 13-*.md's original fail-closed
// default for UNCLASSIFIED.
class RegulatoryScheduleTest {

    @Test
    void onlyControlledIsNotDispensable() {
        for (RegulatorySchedule schedule : RegulatorySchedule.values()) {
            boolean expected = schedule != RegulatorySchedule.CONTROLLED;
            assertThat(schedule.isDispensable()).as(schedule.name()).isEqualTo(expected);
        }
    }
}
