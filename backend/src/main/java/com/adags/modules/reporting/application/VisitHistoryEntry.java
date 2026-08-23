package com.adags.modules.reporting.application;

import java.time.Instant;
import java.util.UUID;

// 05-receptionist.md §1.1 ⚠1: the receptionist's restricted visit-history
// projection — encounter date/type/state/department/attending provider/
// disposition and invoice/payment status ONLY. Deliberately carries no
// field for chief complaint, vitals, acuity, diagnosis, note, or result —
// a projection cannot leak a column it never selects, unlike a filter that
// can be forgotten later.
public record VisitHistoryEntry(
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
}
