package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.SupplierRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 12-pharmacist.md §7.2: "Supplier | Managed list, not free text" — the
// restock form's picker. Read-only, same treatment as
// InsuranceSchemeController: no dedicated permission for this small
// managed list, gated on STOCK_RESTOCK, the action it's actually in
// service of.
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository supplierRepository;
    private final AuthorizationService authorizationService;

    public SupplierController(SupplierRepository supplierRepository, AuthorizationService authorizationService) {
        this.supplierRepository = supplierRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<SupplierResponse> list(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "STOCK_RESTOCK")) {
            throw new AccessDeniedException("Missing permission: STOCK_RESTOCK");
        }
        return supplierRepository.findAllActive().stream().map(SupplierResponse::from).toList();
    }
}
