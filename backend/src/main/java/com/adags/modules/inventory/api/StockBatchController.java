package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.RestockLedgerEntry;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.Money;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

// Legacy single-batch restock endpoint. Access is checked against the
// destination store; new deliveries use /api/stock-receipts.
@RestController
@RequestMapping("/api/stock-batches")
public class StockBatchController {

    private static final String DEFAULT_CURRENCY = "TZS";

    private final RestockService restockService;
    private final StockBatchRepository stockBatchRepository;
    private final AuthorizationService authorizationService;
    private final StoreRepository storeRepository;

    public StockBatchController(
            RestockService restockService,
            StockBatchRepository stockBatchRepository,
            AuthorizationService authorizationService,
            StoreRepository storeRepository
    ) {
        this.restockService = restockService;
        this.stockBatchRepository = stockBatchRepository;
        this.authorizationService = authorizationService;
        this.storeRepository = storeRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockBatchResponse restock(@Valid @RequestBody RestockRequest request, @AuthenticationPrincipal Actor actor) {
        var store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Store not found"));
        if (!authorizationService.hasPermission(actor, "STOCK_RESTOCK",
                ScopeRef.of(ScopeType.STORE, store.id()))) {
            throw new AccessDeniedException("Missing permission: STOCK_RESTOCK");
        }
        String currency = request.unitCostCurrencyCode() == null || request.unitCostCurrencyCode().isBlank()
                ? DEFAULT_CURRENCY : request.unitCostCurrencyCode();
        StockBatch batch = restockService.restock(new RestockCommand(request.itemId(), request.storeId(),
                request.batchNumber(), request.expiryDate(), request.quantity(),
                new Money(request.unitCostAmountMinor(), currency), request.supplierId(),
                request.deliveryNoteRef(), request.notes(), actor.userId()));
        return StockBatchResponse.from(batch);
    }

    // 18-admin.md §8 medication-and-pharmacy expense view: every restock
    // the pharmacist recorded in the window, so the admin doesn't
    // re-enter what has already been captured on the receive form.
    // STOCK_READ gates every other read on stock; the accountant/admin
    // hold it (V5) for exactly this "what did we buy" view.
    @GetMapping
    public List<RestockLedgerEntry> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "STOCK_READ")) {
            throw new AccessDeniedException("Missing permission: STOCK_READ");
        }
        var allowedStores = storeRepository.findAllActive().stream()
                .filter(store -> authorizationService.hasPermission(actor, "STOCK_READ",
                        ScopeRef.of(ScopeType.STORE, store.id())))
                .map(store -> store.id()).toList();
        if (allowedStores.isEmpty()) return List.of();
        return stockBatchRepository.findRestocksBetween(
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), allowedStores);
    }
}
