package com.adags.modules.clinical.domain;

import java.time.Instant;
import java.util.UUID;

// 06-triage.md §2.4 / 07-doctor.md §6. Append-only for the clinical content
// (description/reaction/severity/source/recordedBy/recordedAt) — root
// CLAUDE.md "Data": nothing clinical is ever hard-deleted or updated in
// place, so a new observation is always a new row (via supersedesId, never
// an edit). confirmationStatus/confirmedBy/confirmedAt/confirmationNote are
// the one exception: §6.1 calls the confirm/refute action "documentary, not
// functional" — it updates metadata ABOUT this same row (who reviewed it,
// when) rather than rewriting what was observed, so ReconcileAllergyRecordService
// updates those fields in place. patient_id is one of the two cross-module
// FKs a non-patients module may hold (backend CLAUDE.md "Module boundary
// rules" #3).
public record AllergyRecord(
        UUID id,
        UUID patientId,
        UUID encounterId,
        String description,
        String reaction,
        String severity,
        AllergySource source,
        UUID recordedBy,
        Instant recordedAt,
        AllergyConfirmationStatus confirmationStatus,
        UUID confirmedBy,
        Instant confirmedAt,
        String confirmationNote,
        UUID supersedesId
) {

    public static AllergyRecord record(UUID patientId, UUID encounterId, String description, String reaction,
                                        String severity, AllergySource source, UUID recordedBy, UUID supersedesId) {
        return new AllergyRecord(null, patientId, encounterId, description, reaction, severity, source,
                recordedBy, null, AllergyConfirmationStatus.UNCONFIRMED, null, null, null, supersedesId);
    }
}
