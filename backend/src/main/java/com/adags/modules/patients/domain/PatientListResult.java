package com.adags.modules.patients.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// The admin Patients page opens on this list (newest registration first),
// so the admin can review who receptionists have registered without having
// to type a name first. Deliberate deviation from 05-receptionist.md §5.1
// "no legitimate list-all-patients screen" — restored by product request.
// Safeguards kept: bounded page size, audited on every call, gated on
// PATIENT_SEARCH.
//
// insuranceSchemeCode/Name are the patient's most recent active policy
// today (LATERAL join, one row per patient). Null = no active policy =
// cash. This is what the "Payment method" filter matches against — payer
// category is otherwise a per-encounter concept, not a patient attribute.
public record PatientListResult(
        UUID id,
        String hospitalNo,
        String fullName,
        LocalDate dateOfBirth,
        boolean dobIsEstimated,
        String phone,
        Instant createdAt,
        String insuranceSchemeCode,
        String insuranceSchemeName
) {
}
