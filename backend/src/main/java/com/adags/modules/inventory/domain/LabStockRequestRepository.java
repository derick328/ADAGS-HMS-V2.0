package com.adags.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabStockRequestRepository {

    LabStockRequest insert(LabStockRequest request);

    Optional<LabStockRequest> findById(UUID id);

    /** Persists releasedQuantity/status/responseNotes/reviewedBy/reviewedAt. */
    LabStockRequest update(LabStockRequest request);

    /** 15-lab-technician.md §5: the Stock Requests screen's own list + pending-count badge, newest first. */
    List<LabStockRequest> findByRequestedBy(UUID requestedBy);
}
