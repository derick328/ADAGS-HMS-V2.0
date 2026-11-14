package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.shared.authz.AuditRecorder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Versioned, product-specific allowed administration routes. Empty means unrestricted. */
@Service
public class MedicineRouteService {
    public record RouteConfiguration(List<String> routes, String concentration) {}
    private final NamedParameterJdbcTemplate jdbc;
    private final ItemRepository items;
    private final AuditRecorder audit;

    public MedicineRouteService(NamedParameterJdbcTemplate jdbc, ItemRepository items, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.items = items;
        this.audit = audit;
    }

    public RouteConfiguration current(UUID itemId) {
        List<RouteConfiguration> rows = jdbc.query("""
                SELECT routes_csv, concentration FROM medicine_route_configuration
                WHERE item_id = :itemId ORDER BY version DESC LIMIT 1
                """, new MapSqlParameterSource("itemId", itemId), (rs, row) ->
                new RouteConfiguration(Arrays.asList(rs.getString("routes_csv").split(",")),
                        rs.getString("concentration")));
        return rows.isEmpty() ? new RouteConfiguration(List.of(), null) : rows.getFirst();
    }

    @Transactional
    public RouteConfiguration configure(UUID itemId, List<String> routes, String concentration,
                                        String reason, UUID actorId) {
        var item = items.findById(itemId).orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (routes == null || routes.isEmpty() || routes.size() > 20 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Select routes and provide a configuration reason.");
        }
        List<String> normalized = routes.stream().map(route -> route == null ? "" : route.trim().toUpperCase(Locale.ROOT)).toList();
        if (normalized.stream().anyMatch(route -> !route.matches("[A-Z][A-Z _-]{1,39}"))
                || normalized.stream().distinct().count() != normalized.size()) {
            throw new IllegalArgumentException("Routes must be distinct names of 2 to 40 letters.");
        }
        jdbc.getJdbcTemplate().execute("LOCK TABLE stock_batch, prescription_line IN SHARE MODE");
        RouteConfiguration previous = current(itemId);
        String verifiedConcentration = concentration == null || concentration.isBlank() ? item.strength() : concentration.trim();
        if ((verifiedConcentration == null || verifiedConcentration.isBlank()) && previous.concentration() != null) {
            verifiedConcentration = previous.concentration();
        }
        if (item.strength() != null && !item.strength().isBlank() && concentration != null
                && !concentration.isBlank() && !item.strength().equalsIgnoreCase(concentration.trim())) {
            throw new IllegalArgumentException("Concentration differs from the catalogue presentation. Create a distinct product for a different strength.");
        }
        if (previous.concentration() != null && verifiedConcentration != null
                && !previous.concentration().equalsIgnoreCase(verifiedConcentration)) {
            throw new IllegalArgumentException("Changing concentration requires a distinct catalogue product.");
        }
        if (item.strength() == null && previous.concentration() == null && verifiedConcentration != null) {
            Long references = jdbc.queryForObject("""
                    SELECT (SELECT COUNT(*) FROM stock_batch WHERE item_id = :itemId)
                         + (SELECT COUNT(*) FROM prescription_line WHERE item_id = :itemId)
                    """, new MapSqlParameterSource("itemId", itemId), Long.class);
            if (references != null && references > 0) {
                throw new IllegalArgumentException("Concentration cannot be assigned after stock or prescriptions exist. Create a distinct product.");
            }
        }
        if (com.adags.shared.kernel.DosageForm.fromCatalogue(item.form()) == com.adags.shared.kernel.DosageForm.INJECTION
                && (verifiedConcentration == null || verifiedConcentration.isBlank())) {
            throw new IllegalArgumentException("Injection requires a documented product concentration.");
        }
        jdbc.queryForObject("SELECT id FROM item WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", itemId), UUID.class);
        jdbc.queryForObject("""
                INSERT INTO medicine_route_configuration
                  (item_id, version, routes_csv, concentration, reason, configured_by)
                SELECT :itemId, COALESCE(MAX(version), 0) + 1, :routes, :concentration, :reason, :actorId
                FROM medicine_route_configuration WHERE item_id = :itemId
                RETURNING id
                """, new MapSqlParameterSource().addValue("itemId", itemId)
                .addValue("routes", String.join(",", normalized))
                .addValue("concentration", verifiedConcentration)
                .addValue("reason", reason.trim()).addValue("actorId", actorId), UUID.class);
        audit.record(actorId, "MEDICINE_ROUTES_CONFIGURE", "ITEM", itemId, null);
        return new RouteConfiguration(normalized, verifiedConcentration);
    }
}
