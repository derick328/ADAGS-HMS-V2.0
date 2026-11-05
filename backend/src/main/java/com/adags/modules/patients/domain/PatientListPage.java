package com.adags.modules.patients.domain;

import java.util.List;

// One page of the registration list plus the total the same filters match,
// so a caller can render "page n of m" without a second round trip.
// Page size stays bounded server-side (SearchPatientsService.MAX_PAGE_SIZE)
// — see PatientListResult for why this list exists at all.
public record PatientListPage(
        int page,
        int pageSize,
        long totalCount,
        List<PatientListResult> items
) {
}
