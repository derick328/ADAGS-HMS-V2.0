package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.UUID;

// 17-ward-nurse.md §4.3: "hasAlerts is set and nothing follows... a flag
// nobody is told about is a record, not an alert." Same shape as
// laboratory.CriticalResultNotification, replicated rather than reused —
// root backend/CLAUDE.md "Modules" rules out a cross-module FK to a
// laboratory table, and ClinicalLeadDirectory is not facade-exposed.
public record WardAlertNotification(
        UUID id,
        UUID admissionId,
        WardAlertSourceType sourceType,
        /** Polymorphic — a vital_signs.id, wound_care.id, or medication_administration.id depending on sourceType. No FK, same reasoning vital_signs.context_ref_id documents. */
        UUID sourceId,
        String detail,
        UUID notifiedUserId,
        Instant notifiedAt,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        Instant escalatedAt,
        UUID escalatedToUserId,
        Instant createdAt
) {

    public static WardAlertNotification raise(UUID admissionId, WardAlertSourceType sourceType, UUID sourceId,
                                                String detail, UUID notifiedUserId) {
        return new WardAlertNotification(
                null, admissionId, sourceType, sourceId, detail, notifiedUserId, null, null, null, null, null, null);
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    public boolean isEscalated() {
        return escalatedAt != null;
    }
}
