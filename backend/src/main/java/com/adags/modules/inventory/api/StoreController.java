package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Store;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 15-lab-technician.md §5/§11: resolves "this technician's own lab
// sub-store" so the Stock Requests form has a requestingStoreId without a
// store picker screen — no admin store-management UI exists yet (18-admin.md
// doesn't cover it), so the store is lazily created on first use rather
// than requiring one to be provisioned in advance.
@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private static final String LAB_STORE_TYPE = "LAB";
    private static final String LAB_STORE_NAME = "Laboratory store";

    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;

    public StoreController(StoreRepository storeRepository, AuthorizationService authorizationService) {
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/mine")
    public StoreResponse mine(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_STOCK_REQUEST_CREATE")) {
            throw new AccessDeniedException("Missing permission: LAB_STOCK_REQUEST_CREATE");
        }
        UUID facilityId = resolveFacilityId(actor);
        Store store = storeRepository.findByFacilityIdAndType(facilityId, LAB_STORE_TYPE)
                .orElseGet(() -> createLabStore(facilityId));
        return StoreResponse.from(store);
    }

    // LabOrderController's own comment: "a lab serves a whole facility, not
    // one ward or department... a facility-scoped assignment is the right
    // granularity here" — a correctly provisioned LAB_TECHNICIAN assignment
    // is FACILITY-scoped, so this is expected to always resolve.
    private UUID resolveFacilityId(Actor actor) {
        return authorizationService.activeAssignments(actor).stream()
                .filter(assignment -> assignment.permissionCodes().contains("LAB_STOCK_REQUEST_CREATE"))
                .filter(assignment -> assignment.scope().type() == ScopeType.FACILITY)
                .map(assignment -> assignment.scope().id())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No facility-scoped LAB_STOCK_REQUEST_CREATE assignment for user " + actor.userId()));
    }

    private Store createLabStore(UUID facilityId) {
        try {
            return storeRepository.insert(Store.create(LAB_STORE_NAME, LAB_STORE_TYPE, facilityId));
        } catch (DataIntegrityViolationException raceLost) {
            // idx_store_facility_type (V64): another request created it first.
            return storeRepository.findByFacilityIdAndType(facilityId, LAB_STORE_TYPE).orElseThrow(() -> raceLost);
        }
    }
}
