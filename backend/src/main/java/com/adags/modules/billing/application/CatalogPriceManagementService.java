package com.adags.modules.billing.application;

import com.adags.shared.authz.AuditRecorder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogPriceManagementService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditRecorder audit;

    public CatalogPriceManagementService(NamedParameterJdbcTemplate jdbc, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public long updateCashPrice(UUID itemId, long expectedMinor, long newMinor, UUID actorId) {
        if (expectedMinor < 0 || newMinor < 0)
            throw new IllegalArgumentException("Prices must be nonnegative integer TZS amounts.");
        // Lock the service row so two administrators cannot both validate the same old price.
        var item = jdbc.query("SELECT id, code, name, is_active FROM service_item WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", itemId), (rs, row) -> Map.of(
                        "active", rs.getBoolean("is_active"), "code", rs.getString("code")));
        if (item.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalogue item not found");
        if (!(boolean) item.get(0).get("active"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inactive catalogue items cannot be repriced");

        var current = jdbc.query("""
                SELECT ple.id, ple.price_list_id, ple.amount_minor
                  FROM price_list_entry ple JOIN price_list pl ON pl.id = ple.price_list_id
                 WHERE ple.service_item_id = :id AND pl.payer_category = 'CASH'
                   AND ple.valid_to IS NULL
                """, new MapSqlParameterSource("id", itemId), (rs, row) -> new CurrentPrice(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("price_list_id"), rs.getLong("amount_minor")));
        if (current.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Item has no current CASH price");
        CurrentPrice old = current.get(0);
        if (old.amountMinor() != expectedMinor)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Price changed; refresh the catalogue before saving");
        if (newMinor == old.amountMinor()) return newMinor;

        jdbc.update("UPDATE price_list_entry SET valid_to = CURRENT_DATE WHERE id = :id AND valid_to IS NULL",
                new MapSqlParameterSource("id", old.id()));
        jdbc.update("""
                INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, valid_from, created_by)
                VALUES (:listId, :itemId, :amount, 'TZS', CURRENT_DATE, :actorId)
                """, new MapSqlParameterSource().addValue("listId", old.listId())
                .addValue("itemId", itemId).addValue("amount", newMinor).addValue("actorId", actorId));
        audit.record(actorId, "CATALOG_PRICE_CHANGED", "service_item", itemId, null);
        return newMinor;
    }

    public List<PriceChange> history(UUID itemId) {
        return jdbc.query("""
                SELECT ple.id, si.id AS item_id, si.code, si.name, ple.amount_minor,
                       LEAD(ple.amount_minor) OVER (ORDER BY ple.created_at DESC, ple.id DESC) AS previous_minor,
                       ple.created_by, ple.created_at, ple.valid_to
                  FROM price_list_entry ple JOIN service_item si ON si.id = ple.service_item_id
                  JOIN price_list pl ON pl.id = ple.price_list_id
                 WHERE si.id = :id AND pl.payer_category = 'CASH'
                 ORDER BY ple.created_at DESC, ple.id DESC
                """, new MapSqlParameterSource("id", itemId), (rs, row) -> new PriceChange(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("item_id"), rs.getString("code"),
                rs.getString("name"), rs.getLong("amount_minor"),
                rs.getObject("previous_minor") == null ? null : rs.getLong("previous_minor"),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant(), rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toLocalDate()));
    }

    private record CurrentPrice(UUID id, UUID listId, long amountMinor) {}
    public record PriceChange(UUID entryId, UUID itemId, String code, String name, long priceMinor, Long previousPriceMinor,
                              UUID changedBy, java.time.Instant changedAt, java.time.LocalDate validTo) {}
}
