package com.adags.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface LabStockRequestRepository {

    LabStockRequest insert(LabStockRequest request);

    Optional<LabStockRequest> findById(UUID id);

    /** Persists releasedQuantity/status/responseNotes/reviewedBy/reviewedAt. */
    LabStockRequest update(LabStockRequest request);
}
