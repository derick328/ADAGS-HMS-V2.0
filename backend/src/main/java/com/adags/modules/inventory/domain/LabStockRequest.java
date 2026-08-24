package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §7/§8: "A release is a transfer, not a dispense... no
// billing gate." One request can receive several releases (partial
// release permitted) before it's fully satisfied, rejected, or explicitly
// closed with the remainder outstanding.
public record LabStockRequest(
        UUID id,
        UUID itemId,
        int requestedQuantity,
        int releasedQuantity,
        UUID requestingStoreId,
        UUID requestedBy,
        Instant requestedAt,
        LabStockRequestStatus status,
        String responseNotes,
        UUID reviewedBy,
        Instant reviewedAt,
        // 15-lab-technician.md §11: "request_notes | Optional, max 500 chars"
        // — the requester's own note, distinct from responseNotes (the
        // pharmacist's reject/close note). Length enforced by V63's CHECK.
        String requestNotes
) {

    public static LabStockRequest request(UUID itemId, int requestedQuantity, UUID requestingStoreId, UUID requestedBy, String requestNotes) {
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("requestedQuantity must be > 0.");
        }
        return new LabStockRequest(null, itemId, requestedQuantity, 0, requestingStoreId, requestedBy, null,
                LabStockRequestStatus.PENDING, null, null, null, requestNotes);
    }

    public int remaining() {
        return requestedQuantity - releasedQuantity;
    }

    /** Pure state transition — the caller performs the actual two-movement stock transfer separately, in the same transaction. */
    public LabStockRequest applyRelease(int quantity, UUID reviewedBy) {
        requireOpen();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0.");
        }
        if (quantity > remaining()) {
            throw new LabStockRequestExceedsRequestedException(id, quantity, remaining());
        }
        int newReleased = releasedQuantity + quantity;
        LabStockRequestStatus newStatus = newReleased == requestedQuantity
                ? LabStockRequestStatus.RELEASED : LabStockRequestStatus.PARTIALLY_RELEASED;
        return new LabStockRequest(id, itemId, requestedQuantity, newReleased, requestingStoreId, requestedBy,
                requestedAt, newStatus, responseNotes, reviewedBy, Instant.now(), requestNotes);
    }

    /** §7: "Reject requires a note." Only from PENDING — a request with any release already made must be closed, not rejected. */
    public LabStockRequest reject(String responseNotes, UUID reviewedBy) {
        if (status != LabStockRequestStatus.PENDING) {
            throw new LabStockRequestNotOpenException(id, status);
        }
        requireNote(responseNotes);
        return new LabStockRequest(id, itemId, requestedQuantity, releasedQuantity, requestingStoreId, requestedBy,
                requestedAt, LabStockRequestStatus.REJECTED, responseNotes, reviewedBy, Instant.now(), requestNotes);
    }

    /** §7: "the remainder stays open or closes with a note" — the pharmacist saying no more will come. */
    public LabStockRequest close(String responseNotes, UUID reviewedBy) {
        requireOpen();
        requireNote(responseNotes);
        return new LabStockRequest(id, itemId, requestedQuantity, releasedQuantity, requestingStoreId, requestedBy,
                requestedAt, LabStockRequestStatus.CLOSED, responseNotes, reviewedBy, Instant.now(), requestNotes);
    }

    private void requireOpen() {
        if (status != LabStockRequestStatus.PENDING && status != LabStockRequestStatus.PARTIALLY_RELEASED) {
            throw new LabStockRequestNotOpenException(id, status);
        }
    }

    private void requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("responseNotes is required.");
        }
    }

    public LabStockRequest withId(UUID id) {
        return new LabStockRequest(id, itemId, requestedQuantity, releasedQuantity, requestingStoreId, requestedBy,
                requestedAt, status, responseNotes, reviewedBy, reviewedAt, requestNotes);
    }
}
