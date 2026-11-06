package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.StockAdjustmentService;
import com.adags.modules.inventory.application.StockAdjustmentService.Adjustment;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/stock-adjustments")
public class StockAdjustmentController {
    private final StockAdjustmentService service;
    private final StockBatchRepository batches;
    private final StoreRepository stores;
    private final AuthorizationService authorization;
    private final NamedParameterJdbcTemplate jdbc;

    public StockAdjustmentController(StockAdjustmentService service, StockBatchRepository batches,
                                     StoreRepository stores, AuthorizationService authorization,
                                     NamedParameterJdbcTemplate jdbc) {
        this.service = service;
        this.batches = batches;
        this.stores = stores;
        this.authorization = authorization;
        this.jdbc = jdbc;
    }

    public record Request(@NotNull UUID batchId, @NotBlank String type, int quantityDelta,
                          @NotBlank String reason, @NotBlank String idempotencyKey) {
    }

    public record Review(boolean approve, String reason) {
    }

    public record PhysicalBatch(UUID batchId, String itemName, String itemCode, String batchNumber,
                                String baseUnit, String status, java.time.LocalDate expiryDate,
                                int quantity, UUID storeId) {
    }

    @GetMapping("/batches")
    public List<PhysicalBatch> batches(@RequestParam UUID storeId, @AuthenticationPrincipal Actor actor) {
        require(actor, "STOCK_READ", storeId);
        return jdbc.query("""
                SELECT b.id AS batch_id, i.name AS item_name, i.code AS item_code, b.batch_number,
                       i.unit AS base_unit,
                       CASE WHEN b.expiry_date <= (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date
                            THEN 'EXPIRED' ELSE b.status END AS status,
                       b.expiry_date, sb.quantity, sb.store_id
                FROM stock_balance sb JOIN stock_batch b ON b.id = sb.batch_id
                JOIN item i ON i.id = sb.item_id
                WHERE sb.store_id = :storeId AND sb.quantity > 0
                ORDER BY i.name, b.expiry_date
                """, new MapSqlParameterSource("storeId", storeId),
                (rs, row) -> new PhysicalBatch((UUID) rs.getObject("batch_id"),
                        rs.getString("item_name"), rs.getString("item_code"),
                        rs.getString("batch_number"), rs.getString("base_unit"),
                        rs.getString("status"), rs.getDate("expiry_date").toLocalDate(),
                        rs.getInt("quantity"), (UUID) rs.getObject("store_id")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Adjustment request(@Valid @RequestBody Request request, @AuthenticationPrincipal Actor actor) {
        var batch = batches.findById(request.batchId())
                .orElseThrow(() -> new NoSuchElementException("Batch not found"));
        require(actor, "STOCK_ADJUST_REQUEST", batch.storeId());
        return service.request(batch.id(), request.type(), request.quantityDelta(),
                request.reason(), request.idempotencyKey(), actor.userId());
    }

    @PostMapping("/{id}/review")
    public Adjustment review(@PathVariable UUID id, @RequestBody Review review,
                             @AuthenticationPrincipal Actor actor) {
        Adjustment adjustment = service.findById(id);
        require(actor, "STOCK_WRITE_OFF_APPROVE", adjustment.storeId());
        return service.review(id, review.approve(), review.reason(), actor.userId());
    }

    @GetMapping
    public List<Adjustment> list(@RequestParam UUID storeId, @AuthenticationPrincipal Actor actor) {
        require(actor, "STOCK_READ", storeId);
        return service.forStore(storeId);
    }

    private void require(Actor actor, String permission, UUID storeId) {
        var store = stores.findById(storeId).orElseThrow(() -> new NoSuchElementException("Store not found"));
        if (!authorization.hasPermission(actor, permission,
                ScopeRef.of(ScopeType.STORE, store.id()))) {
            throw new AccessDeniedException("Missing scoped permission: " + permission);
        }
    }
}
