package com.adags.modules.surgery.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// procedureServiceItemCode/procedureName mirror the Diagnosis
// icd10Code/icd10TitleSnapshot split (07-doctor.md §4.1) — the code is
// what BillingFacade.postServiceCharge resolves a price against (root
// CLAUDE.md "Billing": "Nobody types a price"), procedureName is the
// catalogue name snapshotted at scheduling time.
//
// invoiceLineId is never null: ScheduleSurgeryService posts the procedure
// fee charge (billingFacade.postServiceCharge) before this row exists,
// generating this record's own id up front the same way
// SubmitTriageAssessmentService pre-generates triageAssessmentId — see
// withId.
public record SurgeryOrder(
        UUID id,
        UUID encounterId,
        String procedureServiceItemCode,
        String procedureName,
        String surgeryType,
        SurgeryUrgency urgency,
        String anesthesiaType,
        LocalDate scheduledDate,
        String timeSlot,
        UUID theatreId,
        int estimatedDurationMinutes,
        List<UUID> assignedNurses,
        String preOpNotes,
        boolean consentObtained,
        String consentDocumentKey,
        SurgeryOrderStatus status,
        UUID invoiceLineId,
        UUID scheduledBy,
        Instant scheduledAt,
        int version,
        SurgeryDisposition disposition,
        UUID dispositionRecordedBy,
        Instant dispositionRecordedAt,
        CancellationReason cancellationReason,
        UUID cancelledBy,
        Instant cancelledAt,
        /** Phase 8 item 8: set once by EmergencyDraftGovernanceJob — "48h -> CLINICAL_LEAD governance queue," never re-fired. */
        Instant governanceEscalatedAt
) {

    /**
     * root CLAUDE.md task text (Phase 8 item 1): "Consent blocks
     * SCHEDULING, not billing. No signed document, no theatre slot." A
     * checkbox alone is not consent — consentDocumentKey (the PDF
     * reference) must also be present. Returns a row with id = null and
     * status = SCHEDULED, ready for withId then insert.
     */
    public static SurgeryOrder schedule(
            UUID encounterId, String procedureServiceItemCode, String procedureName, String surgeryType,
            SurgeryUrgency urgency, String anesthesiaType, LocalDate scheduledDate, String timeSlot,
            UUID theatreId, int estimatedDurationMinutes, List<UUID> assignedNurses, String preOpNotes,
            boolean consentObtained, String consentDocumentKey, UUID invoiceLineId, UUID scheduledBy
    ) {
        if (!consentObtained || consentDocumentKey == null || consentDocumentKey.isBlank()) {
            throw new ConsentNotObtainedException(encounterId);
        }
        return new SurgeryOrder(null, encounterId, procedureServiceItemCode, procedureName, surgeryType, urgency,
                anesthesiaType, scheduledDate, timeSlot, theatreId, estimatedDurationMinutes,
                assignedNurses == null ? List.of() : assignedNurses, preOpNotes, true, consentDocumentKey,
                SurgeryOrderStatus.SCHEDULED, invoiceLineId, scheduledBy, null, 0, null, null, null, null, null, null,
                null);
    }

    /**
     * Phase 8 item 8: "urgency = IMMEDIATE -> status = DRAFT_EMERGENCY,
     * free-text fields permitted, billing gate bypassed." No consent
     * check, no catalogue code, no invoice line — procedureName is
     * whatever free text the surgeon typed, reconciled to a catalogue
     * item later (ReconcileEmergencyDraftProcedureNameService).
     */
    public static SurgeryOrder draftEmergency(
            UUID encounterId, String procedureName, String surgeryType, String anesthesiaType,
            LocalDate scheduledDate, String timeSlot, UUID theatreId, int estimatedDurationMinutes,
            List<UUID> assignedNurses, String preOpNotes, UUID scheduledBy
    ) {
        return new SurgeryOrder(null, encounterId, null, procedureName, surgeryType, SurgeryUrgency.IMMEDIATE,
                anesthesiaType, scheduledDate, timeSlot, theatreId, estimatedDurationMinutes,
                assignedNurses == null ? List.of() : assignedNurses, preOpNotes, false, null,
                SurgeryOrderStatus.DRAFT_EMERGENCY, null, scheduledBy, null, 0, null, null, null, null, null, null,
                null);
    }

    public SurgeryOrder withId(UUID id) {
        return new SurgeryOrder(id, encounterId, procedureServiceItemCode, procedureName, surgeryType, urgency,
                anesthesiaType, scheduledDate, timeSlot, theatreId, estimatedDurationMinutes, assignedNurses,
                preOpNotes, consentObtained, consentDocumentKey, status, invoiceLineId, scheduledBy, scheduledAt,
                version, disposition, dispositionRecordedBy, dispositionRecordedAt, cancellationReason, cancelledBy,
                cancelledAt, governanceEscalatedAt);
    }
}
