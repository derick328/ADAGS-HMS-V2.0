package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.LabStockRequest;
import com.adags.modules.inventory.domain.LabStockRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §7: the two non-release outcomes. "Reject requires a
// note" (from PENDING, nothing released yet); "the remainder... closes
// with a note" (close, once at least PENDING/PARTIALLY_RELEASED, whether
// or not fully satisfied).
@Service
public class ReviewLabStockRequestService {

    private final LabStockRequestRepository labStockRequestRepository;

    public ReviewLabStockRequestService(LabStockRequestRepository labStockRequestRepository) {
        this.labStockRequestRepository = labStockRequestRepository;
    }

    @Transactional
    public LabStockRequest reject(UUID requestId, String responseNotes, UUID reviewedBy) {
        LabStockRequest request = findRequest(requestId);
        return labStockRequestRepository.update(request.reject(responseNotes, reviewedBy));
    }

    @Transactional
    public LabStockRequest close(UUID requestId, String responseNotes, UUID reviewedBy) {
        LabStockRequest request = findRequest(requestId);
        return labStockRequestRepository.update(request.close(responseNotes, reviewedBy));
    }

    private LabStockRequest findRequest(UUID requestId) {
        return labStockRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Lab stock request not found: " + requestId));
    }
}
