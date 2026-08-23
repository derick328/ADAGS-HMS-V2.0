package com.adags.modules.reporting.api;

import com.adags.modules.reporting.application.VisitHistoryEntry;

import java.time.Instant;
import java.util.UUID;

public record VisitHistoryResponse(
        UUID encounterId,
        Instant encounterDate,
        String encounterType,
        String encounterState,
        String departmentName,
        String attendingProviderName,
        UUID invoiceId,
        String invoiceState,
        long outstandingTotalMinor,
        String currencyCode
) {

    public static VisitHistoryResponse from(VisitHistoryEntry entry) {
        return new VisitHistoryResponse(entry.encounterId(), entry.encounterDate(), entry.encounterType(),
                entry.encounterState(), entry.departmentName(), entry.attendingProviderName(), entry.invoiceId(),
                entry.invoiceState(), entry.outstandingTotalMinor(), entry.currencyCode());
    }
}
