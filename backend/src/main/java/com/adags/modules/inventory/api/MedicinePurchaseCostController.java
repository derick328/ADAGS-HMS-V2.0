package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.MedicinePackagingService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/medicines/{itemId}/default-purchase-cost")
public class MedicinePurchaseCostController {
    private final NamedParameterJdbcTemplate jdbc;
    private final MedicinePackagingService packaging;
    private final AuthorizationService authorization;

    public MedicinePurchaseCostController(NamedParameterJdbcTemplate jdbc, MedicinePackagingService packaging,
                                          AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.packaging = packaging;
        this.authorization = authorization;
    }

    public record Cost(String unit, long amountMinor, UUID updatedBy, Instant updatedAt) {}
    public record Update(@NotBlank String unit, @NotNull @PositiveOrZero Long amountMinor) {}

    @GetMapping
    public Cost current(@PathVariable UUID itemId, @AuthenticationPrincipal Actor actor) {
        requireRead(actor);
        return jdbc.query("""
                SELECT unit, amount_minor, updated_by, updated_at
                FROM medicine_default_purchase_cost WHERE item_id = :id
                """, new MapSqlParameterSource("id", itemId), (rs, n) -> new Cost(
                rs.getString("unit"), rs.getLong("amount_minor"),
                (UUID) rs.getObject("updated_by"), rs.getTimestamp("updated_at").toInstant()))
                .stream().findFirst().orElse(null);
    }

    @GetMapping("/history")
    public List<Cost> history(@PathVariable UUID itemId, @AuthenticationPrincipal Actor actor) {
        requireRead(actor);
        return jdbc.query("""
                SELECT unit, amount_minor, changed_by AS updated_by, changed_at AS updated_at
                FROM medicine_default_purchase_cost_history WHERE item_id = :id
                ORDER BY changed_at DESC, id DESC
                """, new MapSqlParameterSource("id", itemId), (rs, n) -> new Cost(
                rs.getString("unit"), rs.getLong("amount_minor"),
                (UUID) rs.getObject("updated_by"), rs.getTimestamp("updated_at").toInstant()));
    }

    @PutMapping
    @Transactional
    public Cost update(@PathVariable UUID itemId, @Valid @RequestBody Update input,
                       @AuthenticationPrincipal Actor actor) {
        if (!authorization.hasPermission(actor, "CATALOG_IMPORT"))
            throw new AccessDeniedException("CATALOG_IMPORT required");
        var selected = packaging.approved(itemId, input.unit());
        var args = new MapSqlParameterSource().addValue("id", itemId)
                .addValue("unit", selected.unit()).addValue("amount", input.amountMinor())
                .addValue("actor", actor.userId());
        jdbc.update("""
                INSERT INTO medicine_default_purchase_cost(item_id, unit, amount_minor, updated_by)
                VALUES (:id, :unit, :amount, :actor)
                ON CONFLICT (item_id) DO UPDATE SET unit = EXCLUDED.unit,
                    amount_minor = EXCLUDED.amount_minor, updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                """, args);
        jdbc.update("""
                INSERT INTO medicine_default_purchase_cost_history(item_id, unit, amount_minor, changed_by)
                VALUES (:id, :unit, :amount, :actor)
                """, args);
        return current(itemId, actor);
    }

    private void requireRead(Actor actor) {
        if (!authorization.hasAnyPermission(actor, "CATALOG_IMPORT", "STOCK_READ"))
            throw new AccessDeniedException("Catalogue or stock read permission required");
    }
}
