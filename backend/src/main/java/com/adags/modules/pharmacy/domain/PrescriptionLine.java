package com.adags.modules.pharmacy.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §5.1/§12: one PrescriptionLine = one invoice line = one
// billing gate check — "gating is per invoice line, never per invoice"
// (root CLAUDE.md "Billing") is structural here, the same way LabOrder
// makes it structural for lab charges. quantity_dispensed is materialised
// (updated in the same transaction as every Dispense against this line),
// not derived by summing DispenseLine rows on every read.
public record PrescriptionLine(
        UUID id,
        UUID prescriptionOrderId,
        UUID itemId,
        int quantityPrescribed,
        int quantityDispensed,
        String dosageInstructions,
        UUID invoiceLineId,
        PrescriptionLineStatus status,
        /** §5.1: governs whether a substitution proposal is possible at all. Set by the prescriber, at prescribing time. */
        boolean substitutionAllowed,
        /** Why a CANCELLED or UNAVAILABLE line got there — null for every other status. */
        String statusReason,
        Instant createdAt
) {

    public static PrescriptionLine create(UUID prescriptionOrderId, UUID itemId, int quantityPrescribed,
                                           String dosageInstructions, UUID invoiceLineId, boolean substitutionAllowed) {
        if (quantityPrescribed <= 0) {
            throw new IllegalArgumentException("quantityPrescribed must be > 0.");
        }
        if (dosageInstructions == null || dosageInstructions.isBlank()) {
            throw new IllegalArgumentException("dosageInstructions is required.");
        }
        return new PrescriptionLine(null, prescriptionOrderId, itemId, quantityPrescribed, 0, dosageInstructions,
                invoiceLineId, PrescriptionLineStatus.AWAITING_DISPENSE, substitutionAllowed, null, null);
    }

    public int remaining() {
        return quantityPrescribed - quantityDispensed;
    }

    /**
     * 12-pharmacist.md §4.4's three-way rule, as a pure state transition:
     * {@code quantity} beyond what remains is a hard block
     * (DispenseExceedsPrescribedException), never a silent clamp. Returns
     * the line with its new quantityDispensed/status — the caller persists it.
     */
    public PrescriptionLine applyDispense(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0.");
        }
        if (quantity > remaining()) {
            throw new DispenseExceedsPrescribedException(id, quantity, remaining());
        }
        int newDispensed = quantityDispensed + quantity;
        PrescriptionLineStatus newStatus = newDispensed == quantityPrescribed
                ? PrescriptionLineStatus.COMPLETED : PrescriptionLineStatus.PARTIALLY_DISPENSED;
        return new PrescriptionLine(id, prescriptionOrderId, itemId, quantityPrescribed, newDispensed,
                dosageInstructions, invoiceLineId, newStatus, substitutionAllowed, statusReason, createdAt);
    }

    /**
     * 12-pharmacist.md §5.2: the pharmacist could not fulfil this line.
     * Allowed while anything remains outstanding — a line already
     * PARTIALLY_DISPENSED can still have its remainder marked unavailable;
     * the dispensed portion's history is untouched.
     */
    public PrescriptionLine markUnavailable(String reason) {
        requireOpen();
        requireReason(reason);
        return new PrescriptionLine(id, prescriptionOrderId, itemId, quantityPrescribed, quantityDispensed,
                dosageInstructions, invoiceLineId, PrescriptionLineStatus.UNAVAILABLE, substitutionAllowed, reason, createdAt);
    }

    /**
     * 12-pharmacist.md §5.1: the original line, cancelled on an approved
     * substitution. Unlike markUnavailable, this requires nothing dispensed
     * yet — "original line cancelled and a replacement posted" describes a
     * clean swap, not a partial one, and mixing dispensing history across
     * two different items on one substituted line is not a case this
     * model tries to represent.
     */
    public PrescriptionLine cancelForSubstitution(String reason) {
        requireOpen();
        requireReason(reason);
        if (quantityDispensed > 0) {
            throw new SubstitutionNotAllowedException(id, "line already has dispensed quantity");
        }
        return new PrescriptionLine(id, prescriptionOrderId, itemId, quantityPrescribed, quantityDispensed,
                dosageInstructions, invoiceLineId, PrescriptionLineStatus.CANCELLED, substitutionAllowed, reason, createdAt);
    }

    private void requireOpen() {
        if (status == PrescriptionLineStatus.COMPLETED || status == PrescriptionLineStatus.CANCELLED
                || status == PrescriptionLineStatus.UNAVAILABLE) {
            throw new PrescriptionLineNotOpenException(id, status);
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required.");
        }
    }

    /** Pre-generated id — same LabOrder.withId pattern PrescriptionOrder uses. */
    public PrescriptionLine withId(UUID id) {
        return new PrescriptionLine(id, prescriptionOrderId, itemId, quantityPrescribed, quantityDispensed,
                dosageInstructions, invoiceLineId, status, substitutionAllowed, statusReason, createdAt);
    }
}
