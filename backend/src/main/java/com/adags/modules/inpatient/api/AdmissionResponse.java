package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.DischargeOrdererRole;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardStatus;

import java.time.Instant;
import java.util.UUID;

public record AdmissionResponse(
        UUID id,
        UUID encounterId,
        UUID patientId,
        UUID facilityId,
        UUID admittingDoctorId,
        WardClass orderedWardClass,
        Instant admittedAt,
        Instant dischargeOrderedAt,
        UUID dischargeOrderedBy,
        DischargeOrdererRole dischargeOrdererRole,
        Instant actualDepartureAt,
        WardStatus wardStatus,
        Instant wardStatusSuggestedCriticalAt,
        int version,
        Instant createdAt
) {
    public static AdmissionResponse from(Admission admission) {
        return new AdmissionResponse(admission.id(), admission.encounterId(), admission.patientId(),
                admission.facilityId(), admission.admittingDoctorId(), admission.orderedWardClass(),
                admission.admittedAt(), admission.dischargeOrderedAt(), admission.dischargeOrderedBy(),
                admission.dischargeOrdererRole(), admission.actualDepartureAt(), admission.wardStatus(),
                admission.wardStatusSuggestedCriticalAt(), admission.version(), admission.createdAt());
    }
}
