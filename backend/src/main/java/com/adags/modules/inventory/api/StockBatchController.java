package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.RestockLedgerEntry;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
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

// 12-pharmacist.md §7.2 (D3, D6): the restock form. STOCK_RESTOCK is
// unscoped here the same way ITEM_CLASSIFY is on ItemController — a store
// belongs to a facility, but this build brief doesn't yet model per-store
// permission scoping (no caller needs it yet); tightened to a
// facility/store ScopeRef once a multi-facility pharmacist role exists to
// distinguish (21-multi-facility.md).
@RestController
@RequestMapping("/api/stock-batches")
public class StockBatchController {

    private static final String DEFAULT_CURRENCY = "TZS";

    private final RestockService restockService;
    private final StockBatchRepository stockBatchRepository;
    private final AuthorizationService authorizationService;

    public StockBatchController(
            RestockService restockService,
            StockBatchRepository stockBatchRepository,
            AuthorizationService authorizationService
    ) {
        this.restockService = restockService;
        this.stockBatchRepository = stockBatchRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockBatchResponse restock(@Valid @RequestBody RestockRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "STOCK_RESTOCK")) {
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
        return stockBatchRepository.findRestocksBetween(
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
