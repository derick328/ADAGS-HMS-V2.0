package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.StockReceiptService;
import com.adags.modules.inventory.application.StockReceiptService.Receipt;
import com.adags.modules.inventory.domain.Store;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-receipts")
public class StockReceiptController {
    private final StockReceiptService service;
    private final StoreRepository stores;
    private final AuthorizationService authorization;

    public StockReceiptController(StockReceiptService service, StoreRepository stores,
                                  AuthorizationService authorization) {
        this.service = service;
        this.stores = stores;
        this.authorization = authorization;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Receipt confirm(@Valid @RequestBody StockReceiptRequest request,
                           @AuthenticationPrincipal Actor actor) {
        Store store = stores.findById(request.storeId())
                .orElseThrow(() -> new NoSuchElementException("Store not found"));
        require(actor, "STOCK_RESTOCK", store.id());
        return service.confirm(request, actor.userId());
    }

    @GetMapping
    public List<Receipt> recent(@RequestParam UUID storeId, @AuthenticationPrincipal Actor actor) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> new NoSuchElementException("Store not found"));
        require(actor, "STOCK_READ", store.id());
        return service.recentForStore(store.id());
    }

    @GetMapping("/{id}")
    public Receipt detail(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Receipt receipt = service.findById(id);
        require(actor, "STOCK_READ", receipt.storeId());
        return receipt;
    }

    private void require(Actor actor, String code, UUID storeId) {
        if (!authorization.hasPermission(actor, code, ScopeRef.of(ScopeType.STORE, storeId))) {
            throw new AccessDeniedException("Missing scoped permission: " + code);
        }
    }
}
