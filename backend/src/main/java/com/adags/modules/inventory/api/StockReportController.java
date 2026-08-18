package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.StockReportService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 12-pharmacist.md §6.1. STOCK_READ gates all four — the accountant role
// already holds it (V5) for stock valuation, the same permission the
// pharmacist uses for current stock/low stock/expiry.
@RestController
@RequestMapping("/api/stock-reports")
public class StockReportController {

    private final StockReportService stockReportService;
    private final AuthorizationService authorizationService;

    public StockReportController(StockReportService stockReportService, AuthorizationService authorizationService) {
        this.stockReportService = stockReportService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/current-stock")
    public List<CurrentStockResponse> currentStock(@AuthenticationPrincipal Actor actor) {
        requireStockRead(actor);
        return stockReportService.currentStock().stream().map(CurrentStockResponse::from).toList();
    }

    @GetMapping("/low-stock")
    public List<LowStockResponse> lowStock(@AuthenticationPrincipal Actor actor) {
        requireStockRead(actor);
        return stockReportService.lowStock().stream().map(LowStockResponse::from).toList();
    }

    @GetMapping("/expiring-batches")
    public List<ExpiringBatchResponse> expiringBatches(@AuthenticationPrincipal Actor actor) {
        requireStockRead(actor);
        return stockReportService.expiringBatches().stream().map(ExpiringBatchResponse::from).toList();
    }

    @GetMapping("/valuation")
    public List<StockValuationResponse> valuation(@AuthenticationPrincipal Actor actor) {
        requireStockRead(actor);
        return stockReportService.stockValuation().stream().map(StockValuationResponse::from).toList();
    }

    private void requireStockRead(Actor actor) {
        if (!authorizationService.hasPermission(actor, "STOCK_READ")) {
            throw new AccessDeniedException("Missing permission: STOCK_READ");
        }
    }
}
