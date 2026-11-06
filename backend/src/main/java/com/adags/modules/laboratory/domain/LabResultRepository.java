package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabResultRepository {

    LabResult insert(LabResult result);

    Optional<LabResult> findById(UUID id);

    Optional<LabResult> findByMachineOutputObjectKey(String objectKey);

    /** Every row for this specimen, including superseded ones — an amendment appends, never replaces. */
    List<LabResult> findBySpecimenId(UUID specimenId);

    /** 15-lab-technician.md §9: "My results" — this technician's own entered-by results, newest first. */
    List<LabResult> findByEnteredBy(UUID enteredBy);

    /** §9's patient-lookup tab, by accession — one specimen has at most one non-superseded result per amendment chain, but every version is returned, newest first. */
    List<LabResult> findByAccessionNo(String accessionNo);

    /** §9's patient-lookup tab, by patient — across every order/specimen/technician, newest first. */
    List<LabResult> findByPatientId(UUID patientId);

    /** 08-doctor-navigation.md §5: doctor's Request History expansion — every result row (including superseded amendments) for one lab order, newest first. */
    List<LabResult> findByLabOrderId(UUID labOrderId);

    /**
     * Sets verification fields — only while verification_state is PENDING.
     * Returns false (a no-op) if the row wasn't PENDING, e.g. a second
     * verify attempt or one racing an amendment.
     */
    boolean verify(UUID resultId, UUID verifiedBy, String verificationComment, String resultVersionHash);

    /** Marks the original row superseded by an amendment — v0.2 §2's AMENDED state. */
    void markAmended(UUID resultId);
}
