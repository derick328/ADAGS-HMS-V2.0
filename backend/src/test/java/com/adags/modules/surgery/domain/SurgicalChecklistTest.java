package com.adags.modules.surgery.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 8 item 4: "The swab and instrument count (count_correct) is NEVER
// waivable, including in emergencies." Plain JUnit — no Spring context.
class SurgicalChecklistTest {

    private final UUID surgeryOrderId = UUID.randomUUID();
    private final UUID completedBy = UUID.randomUUID();

    @Test
    void signInAndTimeOutRejectACountCorrectValue() {
        assertThatThrownBy(() -> SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.SIGN_IN, List.of(), true, null, false, completedBy))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.TIME_OUT, List.of(), false, "note", false, completedBy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signOutRequiresCountCorrectToBeSet() {
        assertThatThrownBy(() -> SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.SIGN_OUT, List.of(), null, null, false, completedBy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signOutWithFalseCountRequiresADiscrepancyNote() {
        assertThatThrownBy(() -> SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.SIGN_OUT, List.of(), false, null, false, completedBy))
                .isInstanceOf(MissingCountDiscrepancyNoteException.class);

        SurgicalChecklist checklist = SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.SIGN_OUT, List.of(), false, "found after recount", false, completedBy);
        assertThat(checklist.countCorrect()).isFalse();
        assertThat(checklist.satisfiesGate()).isFalse();
    }

    @Test
    void signOutIsNeverEmergencyReduced() {
        assertThatThrownBy(() -> SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.SIGN_OUT, List.of(), true, null, true, completedBy))
                .isInstanceOf(EmergencyReductionNotAllowedException.class);
    }

    @Test
    void aTrueCountSatisfiesTheGateAndAFalseCountDoesNot() {
        SurgicalChecklist correct = SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.SIGN_OUT, List.of(), true, null, false, completedBy);
        assertThat(correct.satisfiesGate()).isTrue();

        SurgicalChecklist signIn = SurgicalChecklist.complete(
                surgeryOrderId, ChecklistPhase.SIGN_IN, List.of("patient identity", "site marked"), null, null,
                true, completedBy);
        assertThat(signIn.satisfiesGate()).isTrue();
        assertThat(signIn.isEmergencyReduced()).isTrue();
    }
}
