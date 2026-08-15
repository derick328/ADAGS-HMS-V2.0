package com.adags.modules.patients.domain;

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

    Patient insert(Patient patient, UUID createdByUserId);

    UUID insertNextOfKin(NextOfKin nextOfKin);

    UUID insertAddress(PatientAddress address);

    UUID insertInsurancePolicy(InsurancePolicy policy);

    UUID insertIdentifier(PatientIdentifier identifier);
}
