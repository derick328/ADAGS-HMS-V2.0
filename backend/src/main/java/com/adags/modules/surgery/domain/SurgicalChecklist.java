package com.adags.modules.surgery.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 04-*.md §7. countCorrect is null for SIGN_IN/TIME_OUT, required for
// SIGN_OUT — the swab/instrument count. "NEVER waivable, including in
// emergencies" (task text): there is no override anywhere in this class
// or its callers that lets a FALSE count still satisfy the SIGN_OUT gate
// (see SurgicalChecklistRepository.isPhaseSatisfied).
public record SurgicalChecklist(
        UUID id,
        UUID surgeryOrderId,
        ChecklistPhase phase,
        List<String> items,
        UUID completedBy,
        Instant completedAt,
        Boolean countCorrect,
        String countDiscrepancyNote,
        boolean isEmergencyReduced
) {

    /**
     * Structural checks only — whether isEmergencyReduced is legitimate
     * given the order's actual urgency needs the SurgeryOrder row, so
     * that check lives in CompleteChecklistService, not here (domain has
     * no I/O).
     */
    public static SurgicalChecklist complete(
            UUID surgeryOrderId, ChecklistPhase phase, List<String> items, Boolean countCorrect,
            String countDiscrepancyNote, boolean isEmergencyReduced, UUID completedBy
    ) {
        if (phase == ChecklistPhase.SIGN_OUT) {
            if (isEmergencyReduced) {
                throw new EmergencyReductionNotAllowedException(surgeryOrderId, "SIGN_OUT is never reduced");
            }
            if (countCorrect == null) {
                throw new IllegalArgumentException("count_correct is required for SIGN_OUT.");
            }
        } else if (countCorrect != null) {
            throw new IllegalArgumentException("count_correct only applies to SIGN_OUT.");
        }
        if (Boolean.FALSE.equals(countCorrect) && (countDiscrepancyNote == null || countDiscrepancyNote.isBlank())) {
            throw new MissingCountDiscrepancyNoteException(surgeryOrderId);
        }
        return new SurgicalChecklist(null, surgeryOrderId, phase, items == null ? List.of() : items, completedBy,
                null, countCorrect, countDiscrepancyNote, isEmergencyReduced);
    }

    /** SIGN_IN/TIME_OUT: any row for the phase satisfies it. SIGN_OUT: only countCorrect = TRUE does. */
    public boolean satisfiesGate() {
        return phase != ChecklistPhase.SIGN_OUT || Boolean.TRUE.equals(countCorrect);
    }
}
