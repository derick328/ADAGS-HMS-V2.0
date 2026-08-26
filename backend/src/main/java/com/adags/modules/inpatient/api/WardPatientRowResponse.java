package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardStatus;

import java.time.Instant;
import java.util.UUID;

// 17-ward-nurse.md §3/§9/screen 1-2: one row per patient currently on the
// nurse's ward — everything the Dashboard and Ward Patients list need
// without a client-side join against /api/patients per row. Bed/ward come
// from the admission's active BedAssignment, same join
// AdmissionRepository#findByCurrentWardId used to find the admission.
public record WardPatientRowResponse(
        UUID admissionId,
        UUID encounterId,
        UUID patientId,
        String patientName,
        String hospitalNumber,
        UUID bedId,
        String bedLabel,
        WardClass orderedWardClass,
        WardStatus wardStatus,
        Instant admittedAt,
        Instant dischargeOrderedAt,
        Instant wardStatusSuggestedCriticalAt
) {
}
