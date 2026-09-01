package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Supplier;
import com.adags.modules.inventory.domain.SupplierRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 12-pharmacist.md §7.2: "Supplier | Managed list, not free text" — the
// restock form's picker. GET is gated on STOCK_RESTOCK, the action it's
// actually in service of; POST on the same permission for the "Other"
// branch of the restock form (a pharmacist adding a supplier the seed
// list doesn't cover, mid-restock).
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "STOCK_RESTOCK")) {
            throw new AccessDeniedException("Missing permission: STOCK_RESTOCK");
        }
        String name = request.name().trim();
        // Idempotent on the trimmed, case-insensitive name: two pharmacists
        // adding "Zenufa Labs" mid-restock resolve to the same supplier row
        // rather than two rows with identical labels.
        return supplierRepository.findByNameIgnoreCase(name)
                .map(SupplierResponse::from)
                .orElseGet(() -> SupplierResponse.from(insertWithGeneratedCode(name)));
    }

    private Supplier insertWithGeneratedCode(String name) {
        // Deterministic code from the name so the audit log reads
        // meaningfully ("SUP-ZENUFA" beats a random UUID) — the "-<seq>"
        // suffix resolves a collision (two suppliers whose first token is
        // the same word, e.g. "Zenufa Labs" and "Zenufa Pharma") by
        // retrying, since supplier.code is UNIQUE at the schema level.
        String base = "SUP-" + name.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.length() > 32) base = base.substring(0, 32);
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = attempt == 0 ? base : base + "-" + attempt;
            try {
                return supplierRepository.insert(Supplier.create(code, name));
            } catch (DuplicateKeyException collision) {
                // Try the next suffix; the loop bounds it so a runaway
                // never becomes a hot-loop against the DB.
            }
        }
        throw new IllegalStateException(
                "Could not allocate a unique supplier code for '" + name + "' after 20 attempts.");
    }
}
