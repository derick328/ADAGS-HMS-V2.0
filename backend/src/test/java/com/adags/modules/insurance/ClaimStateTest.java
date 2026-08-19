package com.adags.modules.insurance;

import com.adags.modules.insurance.domain.ClaimState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Phase 7 item 3 part 3: "DRAFT -> SUBMITTED -> APPROVED | REJECTED;
// REJECTED -> RESUBMITTED -> APPROVED | REJECTED." Pure domain logic, no
// Spring context (backend/CLAUDE.md "Testing").
class ClaimStateTest {

    @Test
    void draftCanOnlyMoveToSubmitted() {
        assertThat(ClaimState.DRAFT.canTransitionTo(ClaimState.SUBMITTED)).isTrue();
        assertThat(ClaimState.DRAFT.canTransitionTo(ClaimState.APPROVED)).isFalse();
        assertThat(ClaimState.DRAFT.canTransitionTo(ClaimState.REJECTED)).isFalse();
    }

    @Test
    void submittedCanMoveToApprovedOrRejected() {
        assertThat(ClaimState.SUBMITTED.canTransitionTo(ClaimState.APPROVED)).isTrue();
        assertThat(ClaimState.SUBMITTED.canTransitionTo(ClaimState.REJECTED)).isTrue();
        assertThat(ClaimState.SUBMITTED.canTransitionTo(ClaimState.DRAFT)).isFalse();
    }

    @Test
    void rejectedCanOnlyMoveToResubmitted() {
        assertThat(ClaimState.REJECTED.canTransitionTo(ClaimState.RESUBMITTED)).isTrue();
        assertThat(ClaimState.REJECTED.canTransitionTo(ClaimState.APPROVED)).isFalse();
        assertThat(ClaimState.REJECTED.canTransitionTo(ClaimState.SUBMITTED)).isFalse();
    }

    @Test
    void resubmittedCanMoveToApprovedOrRejected() {
        assertThat(ClaimState.RESUBMITTED.canTransitionTo(ClaimState.APPROVED)).isTrue();
        assertThat(ClaimState.RESUBMITTED.canTransitionTo(ClaimState.REJECTED)).isTrue();
    }

    @Test
    void approvedAndSettledAreTerminalForThisGraph() {
        for (ClaimState target : ClaimState.values()) {
            assertThat(ClaimState.APPROVED.canTransitionTo(target)).isFalse();
            assertThat(ClaimState.SETTLED.canTransitionTo(target)).isFalse();
        }
    }
}
