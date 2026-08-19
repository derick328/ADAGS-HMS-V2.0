package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.UUID;

// 17-ward-nurse.md §5.1: "The most important change in this document" — the
// schedule is generated from the prescription, never typed. This row IS the
// slot; a missed dose is a visible unmarked DUE row past scheduled_at, not
// an absent one (§5.1's whole point).
//
// No drug/dose/route here on purpose — those are properties of the
// prescription line, read live through PharmacyFacade whenever the MAR is
// rendered (§5.1: "dropped as stored fields... duplicating invites
// divergence"). prescriptionLineId carries no DB FK (cross-module).
//
// batchId: per the confirmed model, the batch comes from the Dispense
// record already made when pharmacy dispensed this line. Administering
// links to it — it does not decrement stock again (stock left the ledger
// at dispense).
public record MedicationAdministration(
        UUID id,
        UUID admissionId,
        UUID prescriptionLineId,
        Instant scheduledAt,
        MedicationAdministrationStatus status,
        Instant administeredAt,
        UUID administeredBy,
        String doseGiven,
        UUID batchId,
        /** Required iff Item.requiresAdministrationWitness — CHECK (witness_user_id != administered_by) at the DB. */
        UUID witnessUserId,
        OmissionReason omissionReason,
        String omissionNotes,
        String remarks,
        Instant createdAt
) {

    public static MedicationAdministration dueSlot(UUID admissionId, UUID prescriptionLineId, Instant scheduledAt) {
        return new MedicationAdministration(null, admissionId, prescriptionLineId, scheduledAt,
                MedicationAdministrationStatus.DUE, null, null, null, null, null, null, null, null, null);
    }
}
