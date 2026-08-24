package com.adags.modules.laboratory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabOrderRepository {

    LabOrder insert(LabOrder order);

    Optional<LabOrder> findById(UUID id);

    /** 08-doctor-navigation.md §5: "Request History" — every order this doctor placed, newest first. */
    List<LabOrder> findByOrderedBy(UUID orderedBy);

    /** 08-doctor-navigation.md §3.3: every order on this encounter, across every ordering doctor, newest first. */
    List<LabOrder> findByEncounterId(UUID encounterId);

    /**
     * 15-lab-technician.md §3: the Test Queue's source list — every order
     * with no released result yet (oldest first; the caller applies
     * emergency/urgency ordering on top). "No released result" mirrors
     * LabOrderController#toHistoryResponse's own COMPLETED test: no
     * specimen of this order has a lab_result with released_at set.
     * Unscoped by facility, same convention EncounterController#search
     * already uses for every other queue in this codebase (a permission
     * check, not a facility filter — no queue endpoint here narrows by
     * facility yet).
     */
    List<LabOrder> findOpenOrders();
}
