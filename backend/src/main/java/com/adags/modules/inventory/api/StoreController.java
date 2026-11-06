package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Store;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.authz.ScopeRef;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

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
    private static final String PHARMACY_STORE_TYPE = "MAIN_PHARMACY";
    private static final String PHARMACY_STORE_NAME = "Main Pharmacy";

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
        UUID facilityId = resolveFacilityId(actor, "LAB_STOCK_REQUEST_CREATE");
        Store store = storeRepository.findByFacilityIdAndType(facilityId, LAB_STORE_TYPE)
                .orElseGet(() -> createStore(LAB_STORE_NAME, LAB_STORE_TYPE, facilityId));
        return StoreResponse.from(store);
    }

    // 12-pharmacist.md §7.2: resolves "this pharmacist's main dispensing
    // store" so the restock form has a storeId without a store-management
    // screen (18-admin.md doesn't cover store provisioning yet) — same
    // lazy-create-on-first-use treatment as mine() above, MAIN_PHARMACY
    // instead of LAB.
    @GetMapping("/mine-pharmacy")
    public StoreResponse minePharmacy(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "STOCK_RESTOCK")) {
            throw new AccessDeniedException("Missing permission: STOCK_RESTOCK");
        }
        UUID facilityId = resolveFacilityId(actor, "STOCK_RESTOCK");
        Store store = storeRepository.findByFacilityIdAndType(facilityId, PHARMACY_STORE_TYPE)
                .orElseGet(() -> createStore(PHARMACY_STORE_NAME, PHARMACY_STORE_TYPE, facilityId));
        return StoreResponse.from(store);
    }

    @GetMapping("/authorized-stock")
    public List<StoreResponse> authorizedStock(@AuthenticationPrincipal Actor actor) {
        return storeRepository.findAllActive().stream()
                .filter(store -> authorizationService.hasPermission(actor, "STOCK_READ",
                        ScopeRef.of(ScopeType.STORE, store.id())))
                .map(StoreResponse::from).toList();
    }

    // Fixed id of the bootstrap facility inserted by V4 — the well-known
    // "1" this single-facility deployment operates against. GLOBAL-scoped
    // assignments fall back to this so admins can grant PHARMACIST /
    // LAB_TECHNICIAN with the Users page's default (GLOBAL) scope and
    // still land on a working restock form — the previous behaviour
    // threw a 500 that surfaced as the store query never resolving,
    // which read as "Receive stock button is stuck disabled" on the UI.
    private static final UUID DEFAULT_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // A properly provisioned LAB_TECHNICIAN/PHARMACIST is FACILITY-scoped
    // (a lab or dispensary serves a facility, not a ward or department —
    // see LabOrderController). If the admin granted the role GLOBAL
    // instead, we still resolve to the seed facility; only a role
    // scoped at DEPARTMENT/WARD/etc. is genuinely ambiguous and stays
    // an error so misconfigured setups are still noticed rather than
    // silently defaulting.
    private UUID resolveFacilityId(Actor actor, String permissionCode) {
        var matchingAssignments = authorizationService.activeAssignments(actor).stream()
                .filter(assignment -> assignment.permissionCodes().contains(permissionCode))
                .toList();
        return matchingAssignments.stream()
                .filter(assignment -> assignment.scope().type() == ScopeType.FACILITY)
                .map(assignment -> assignment.scope().id())
                .findFirst()
                .orElseGet(() -> matchingAssignments.stream()
                        .filter(assignment -> assignment.scope().type() == ScopeType.GLOBAL)
                        .findFirst()
                        .map(ignored -> DEFAULT_FACILITY_ID)
                        .orElseThrow(() -> new IllegalStateException(
                                "No facility- or global-scoped " + permissionCode
                                        + " assignment for user " + actor.userId())));
    }

    private Store createStore(String name, String type, UUID facilityId) {
        try {
            return storeRepository.insert(Store.create(name, type, facilityId));
        } catch (DataIntegrityViolationException raceLost) {
            // idx_store_facility_type (V64): another request created it first.
            return storeRepository.findByFacilityIdAndType(facilityId, type).orElseThrow(() -> raceLost);
        }
    }
}
