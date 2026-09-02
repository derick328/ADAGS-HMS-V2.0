package com.adags.modules.surgery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TheatreRepository {

    Theatre insert(Theatre theatre);

    Optional<Theatre> findById(UUID id);

    /** Active-only. This is what schedulers (doctors) consume. */
    List<Theatre> findByFacilityId(UUID facilityId);

    /**
     * Every theatre at the facility, active or not — the admin
     * management view. Kept separate from findByFacilityId so a
     * scheduler cannot see rows the admin has parked, and so no caller
     * ever silently loses the active filter.
     */
    List<Theatre> findAllByFacilityId(UUID facilityId);

    /**
     * Soft-toggle. Root CLAUDE.md "Data": clinical/financial rows are
     * never hard-deleted; the same applies to admin-defined physical
     * resources (Ward, Item, Theatre) — a deactivate is a switch, not a
     * delete, so the doctor's historical surgery orders never point at
     * a row that no longer exists.
     */
    void setActive(UUID id, boolean active);
}
