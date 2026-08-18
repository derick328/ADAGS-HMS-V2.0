package com.adags.modules.encounters.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// The transition guard table agreed before implementation (backend/CLAUDE.md):
// every row is asserted both ways — the allowed edge exists, and everything
// else from that state doesn't.
class EncounterStateTest {

    @Test
    void awaitingTriageAllowsExactlyTheAgreedTargets() {
        assertAllowedTargets(EncounterState.AWAITING_TRIAGE, EnumSet.of(
                EncounterState.AWAITING_PAYMENT, EncounterState.AWAITING_CONSULT,
                EncounterState.LEFT_WITHOUT_BEING_SEEN, EncounterState.CANCELLED_IN_ERROR,
                EncounterState.CLOSED));
    }

    @Test
    void awaitingPaymentAllowsExactlyTheAgreedTargets() {
        assertAllowedTargets(EncounterState.AWAITING_PAYMENT,
                EnumSet.of(EncounterState.AWAITING_CONSULT, EncounterState.ABANDONED_UNPAID));
    }

    @Test
    void awaitingConsultAllowsOnlyInConsult() {
        assertAllowedTargets(EncounterState.AWAITING_CONSULT, EnumSet.of(EncounterState.IN_CONSULT));
    }

    @Test
    void inConsultAllowsExactlyTheAgreedTargets() {
        assertAllowedTargets(EncounterState.IN_CONSULT, EnumSet.of(
                EncounterState.AWAITING_RESULTS, EncounterState.CONSULT_COMPLETE,
                EncounterState.ADMITTED, EncounterState.REFERRED_OUT, EncounterState.DECEASED));
    }

    @Test
    void awaitingResultsAllowsOnlyAwaitingConsult() {
        assertAllowedTargets(EncounterState.AWAITING_RESULTS, EnumSet.of(EncounterState.AWAITING_CONSULT));
    }

    @Test
    void consultCompleteAllowsOnlyPendingSettlement() {
        assertAllowedTargets(EncounterState.CONSULT_COMPLETE, EnumSet.of(EncounterState.PENDING_SETTLEMENT));
    }

    @Test
    void pendingSettlementAllowsOnlyClosed() {
        assertAllowedTargets(EncounterState.PENDING_SETTLEMENT, EnumSet.of(EncounterState.CLOSED));
    }

    @Test
    void admittedAllowsOnlyInWard() {
        assertAllowedTargets(EncounterState.ADMITTED, EnumSet.of(EncounterState.IN_WARD));
    }

    @Test
    void inWardAllowsOnlyDischargeOrdered() {
        assertAllowedTargets(EncounterState.IN_WARD, EnumSet.of(EncounterState.DISCHARGE_ORDERED));
    }

    @Test
    void dischargeOrderedAllowsOnlyDischarged() {
        assertAllowedTargets(EncounterState.DISCHARGE_ORDERED, EnumSet.of(EncounterState.DISCHARGED));
    }

    @ParameterizedTest
    @EnumSource(value = EncounterState.class, names = {
            "LEFT_WITHOUT_BEING_SEEN", "ABANDONED_UNPAID", "CANCELLED_IN_ERROR",
            "REFERRED_OUT", "DECEASED", "CLOSED", "DISCHARGED"
    })
    void terminalStatesAreImmutable(EncounterState terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (EncounterState target : EncounterState.values()) {
            assertThat(terminal.canTransitionTo(target))
                    .as("%s -> %s must be rejected: terminal states are immutable (01-architecture.md §2.1 rule 3)",
                            terminal, target)
                    .isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(value = EncounterState.class, names = {
            "AWAITING_TRIAGE", "AWAITING_PAYMENT", "AWAITING_CONSULT", "IN_CONSULT", "AWAITING_RESULTS",
            "CONSULT_COMPLETE", "PENDING_SETTLEMENT", "ADMITTED", "IN_WARD", "DISCHARGE_ORDERED"
    })
    void nonTerminalStatesAreNotTerminal(EncounterState state) {
        assertThat(state.isTerminal()).isFalse();
    }

    private void assertAllowedTargets(EncounterState from, Set<EncounterState> expectedTargets) {
        for (EncounterState target : EncounterState.values()) {
            boolean expected = expectedTargets.contains(target);
            assertThat(from.canTransitionTo(target))
                    .as("%s -> %s", from, target)
                    .isEqualTo(expected);
        }
    }
}
