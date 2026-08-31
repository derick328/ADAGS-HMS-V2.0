package com.adags.modules.laboratory.api;

import java.util.UUID;

// V67 admin importer visibility (option 2). A row that exists in the
// shared service_item catalogue as TYPE=Laboratory but has no matching
// lab_test row yet — visible in the lab-order typeahead as a "pending
// lab classification" placeholder so the doctor sees the row exists,
// unselectable so nothing is ordered before a lab lead sets specimen
// type / container / TAT on a real lab_test.
public record PendingLabClassificationResponse(
        UUID serviceItemId,
        String code,
        String name,
        String unselectableReason
) {
    public static PendingLabClassificationResponse of(UUID serviceItemId, String code, String name) {
        return new PendingLabClassificationResponse(serviceItemId, code, name,
                "Pending lab classification — a lab lead must set specimen type, container and turnaround time before this can be ordered.");
    }
}
