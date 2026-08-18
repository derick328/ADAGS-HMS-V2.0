package com.adags.modules.clinical.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VitalSignsRepository {

    VitalSigns insert(VitalSigns vitalSigns);

    Optional<VitalSigns> findById(UUID id);

    /** Every reading for the encounter, newest first — the cross-visit trend 17-ward-nurse.md §4.1 asks for. */
    List<VitalSigns> findByEncounterId(UUID encounterId);
}
