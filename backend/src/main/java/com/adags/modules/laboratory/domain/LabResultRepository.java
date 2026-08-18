package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabResultRepository {

    LabResult insert(LabResult result);

    Optional<LabResult> findById(UUID id);

    /** Every row for this specimen, including superseded ones — an amendment appends, never replaces. */
    List<LabResult> findBySpecimenId(UUID specimenId);

    /**
     * Sets verification fields — only while verification_state is PENDING.
     * Returns false (a no-op) if the row wasn't PENDING, e.g. a second
     * verify attempt or one racing an amendment.
     */
    boolean verify(UUID resultId, UUID verifiedBy, String verificationComment, String resultVersionHash);

    /** Marks the original row superseded by an amendment — v0.2 §2's AMENDED state. */
    void markAmended(UUID resultId);
}
