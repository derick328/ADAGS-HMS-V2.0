package com.adags.modules.surgery.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Phase 8 item 3's transition graph, plain JUnit — same idiom as
// encounters.domain.EncounterState's own transition-graph test.
class SurgeryOrderStatusTest {

    @Test
    void scheduledCanMoveToInProgressOrCancelled() {
        assertThat(SurgeryOrderStatus.SCHEDULED.canTransitionTo(SurgeryOrderStatus.IN_PROGRESS)).isTrue();
        assertThat(SurgeryOrderStatus.SCHEDULED.canTransitionTo(SurgeryOrderStatus.CANCELLED)).isTrue();
        assertThat(SurgeryOrderStatus.SCHEDULED.canTransitionTo(SurgeryOrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void inProgressCanMoveToCompletedOrCancelled() {
        assertThat(SurgeryOrderStatus.IN_PROGRESS.canTransitionTo(SurgeryOrderStatus.COMPLETED)).isTrue();
        assertThat(SurgeryOrderStatus.IN_PROGRESS.canTransitionTo(SurgeryOrderStatus.CANCELLED)).isTrue();
        assertThat(SurgeryOrderStatus.IN_PROGRESS.canTransitionTo(SurgeryOrderStatus.SCHEDULED)).isFalse();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        assertThat(SurgeryOrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(SurgeryOrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(SurgeryOrderStatus.COMPLETED.canTransitionTo(SurgeryOrderStatus.IN_PROGRESS)).isFalse();
        assertThat(SurgeryOrderStatus.CANCELLED.canTransitionTo(SurgeryOrderStatus.SCHEDULED)).isFalse();
    }

    @Test
    void scheduledAndInProgressAreNotTerminal() {
        assertThat(SurgeryOrderStatus.SCHEDULED.isTerminal()).isFalse();
        assertThat(SurgeryOrderStatus.IN_PROGRESS.isTerminal()).isFalse();
    }
}
