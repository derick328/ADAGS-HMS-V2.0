package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {
    private final NamedParameterJdbcTemplate jdbc;
    private final StoreRepository stores;
    private final AuthorizationService authorization;

    public StockMovementController(NamedParameterJdbcTemplate jdbc, StoreRepository stores,
                                   AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.stores = stores;
        this.authorization = authorization;
    }

    public record Movement(UUID id, String itemName, String itemCode, String batchNumber,
                           int quantityDelta, String movementType, String referenceType,
                           Instant occurredAt) {
    }

    @GetMapping
    public List<Movement> recent(@RequestParam UUID storeId, @AuthenticationPrincipal Actor actor) {
        var store = stores.findById(storeId).orElseThrow(() -> new NoSuchElementException("Store not found"));
        if (!authorization.hasPermission(actor, "STOCK_READ",
                ScopeRef.of(ScopeType.STORE, store.id()))) {
            throw new AccessDeniedException("Missing scoped permission: STOCK_READ");
        }
        return jdbc.query("""
                SELECT m.id, i.name AS item_name, i.code AS item_code, b.batch_number,
                       m.qty_delta, m.movement_type, m.reference_type, m.occurred_at
                FROM stock_movement m JOIN item i ON i.id = m.item_id
                JOIN stock_batch b ON b.id = m.batch_id
                WHERE m.store_id = :storeId ORDER BY m.occurred_at DESC LIMIT 200
                """, new MapSqlParameterSource("storeId", storeId),
                (rs, row) -> new Movement((UUID) rs.getObject("id"), rs.getString("item_name"),
                        rs.getString("item_code"), rs.getString("batch_number"),
                        rs.getInt("qty_delta"), rs.getString("movement_type"),
                        rs.getString("reference_type"), rs.getTimestamp("occurred_at").toInstant()));
    }
}
