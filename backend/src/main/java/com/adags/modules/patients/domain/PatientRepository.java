package com.adags.modules.patients.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// A documented exception to the facility-filter-by-default rule (root
// CLAUDE.md "Facility"): Patient and everything hanging off it carry no
// facility_id (21-multi-facility.md §3), so there is nothing here for a
// facility-scoping repository base class to apply. Do not add one.
public interface PatientRepository {

    /** Always follows merged_into_id to the surviving record — never the raw row. */
    Optional<Patient> findById(UUID id);

    List<DuplicateCandidate> findDuplicateCandidates(DuplicateCheckQuery query);

    /** Hospital number, name, phone, or national ID — 05-receptionist.md §5.1. */
    List<PatientSearchResult> search(String query, int limit);

    /** The dashboard's "Registered today" tile — a count, not a browsable list (18-admin.md's "no browse-all" applies here too). */
    long countCreatedBetween(Instant from, Instant to);

    /**
     * Recent-registrations list for the admin Patients page — newest first,
     * bounded. See PatientListResult for the deviation note; caller is
     * responsible for the audit record. Optional filters:
     *   - from/to: inclusive on created_at
     *   - payerFilter: "CASH" = no active policy; "NHIF" = active policy
     *     whose scheme code matches NHIF (case-insensitive); any other
     *     non-null value = active non-NHIF policy (the coarse
     *     PayerCategory.fromPayerType simplification applies).
     */
    List<PatientListResult> listRecent(Instant from, Instant to, String payerFilter, int limit);

    List<InsurancePolicy> findInsurancePoliciesByPatientId(UUID patientId);

    Patient insert(Patient patient, UUID createdByUserId);

    UUID insertNextOfKin(NextOfKin nextOfKin);

    UUID insertAddress(PatientAddress address);

    UUID insertInsurancePolicy(InsurancePolicy policy);

    UUID insertIdentifier(PatientIdentifier identifier);
}
