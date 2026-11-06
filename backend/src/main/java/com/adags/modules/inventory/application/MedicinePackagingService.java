package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Flat, product-specific integer factors avoid ambiguous nested conversions. */
@Service
public class MedicinePackagingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ItemRepository items;
    private final AuditRecorder audit;
    private final DomainEventPublisher events;

    public MedicinePackagingService(NamedParameterJdbcTemplate jdbc, ItemRepository items,
                                    AuditRecorder audit, DomainEventPublisher events) {
        this.jdbc = jdbc;
        this.items = items;
        this.audit = audit;
        this.events = events;
    }

    public record Packaging(UUID id, UUID itemId, String unit, int factor, boolean canSplit,
                            int version, String status) {
    }

    public List<Packaging> approvedForItem(UUID itemId) {
        return jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status
                FROM medicine_packaging WHERE item_id = :itemId AND status = 'APPROVED'
                ORDER BY packaging_unit
                """, new MapSqlParameterSource("itemId", itemId), this::map);
    }

    public List<Packaging> pending() {
        return jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status
                FROM medicine_packaging WHERE status = 'PROPOSED' ORDER BY proposed_at
                """, new MapSqlParameterSource(), this::map);
    }

    public Packaging approved(UUID itemId, String unit) {
        Item item = items.findById(itemId).orElseThrow(() -> new NoSuchElementException("Item not found"));
        String normalized = normalize(unit);
        if (normalized.equals(normalize(item.unit()))) {
            return new Packaging(null, itemId, item.unit(), 1, true, 1, "APPROVED");
        }
        return approvedForItem(itemId).stream()
                .filter(p -> p.unit().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unit is not approved for this medicine"));
    }

    @Transactional
    public Packaging propose(UUID itemId, String unit, int factor, boolean canSplit,
                             String reason, UUID actorId) {
        Item item = items.findById(itemId).orElseThrow(() -> new NoSuchElementException("Item not found"));
        String normalized = normalize(unit);
        if (normalized.equals(normalize(item.unit())) || factor <= 1) {
            throw new IllegalArgumentException("A package must contain more than one base unit");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A packaging reason is required");
        }
        // Serialize version allocation and concurrent proposals per product.
        jdbc.queryForObject("SELECT id FROM item WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", itemId), UUID.class);
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0) + 1 FROM medicine_packaging
                WHERE item_id = :itemId AND packaging_unit = :unit
                """, new MapSqlParameterSource().addValue("itemId", itemId).addValue("unit", normalized), Integer.class);
        UUID id = jdbc.queryForObject("""
                INSERT INTO medicine_packaging
                  (item_id, packaging_unit, quantity_in_base_units, can_split, version,
                   status, reason, proposed_by)
                VALUES (:itemId, :unit, :factor, :canSplit, :version,
                        'PROPOSED', :reason, :actorId) RETURNING id
                """, new MapSqlParameterSource().addValue("itemId", itemId).addValue("unit", normalized)
                .addValue("factor", factor).addValue("canSplit", canSplit).addValue("version", next)
                .addValue("reason", reason.trim()).addValue("actorId", actorId), UUID.class);
        audit.record(actorId, "PACKAGING_PROPOSE", "MEDICINE_PACKAGING", id, null);
        events.publish("MedicinePackagingProposed", java.util.Map.of("packagingId", id.toString()));
        return new Packaging(id, itemId, normalized, factor, canSplit, next, "PROPOSED");
    }

    @Transactional
    public Packaging approve(UUID id, UUID actorId) {
        List<Packaging> rows = jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status
                FROM medicine_packaging WHERE id = :id FOR UPDATE
                """, new MapSqlParameterSource("id", id), this::map);
        Packaging proposal = rows.stream().findFirst().orElseThrow(() -> new NoSuchElementException("Packaging proposal not found"));
        if (!proposal.status().equals("PROPOSED")) {
            throw new IllegalStateException("Packaging proposal is no longer pending");
        }
        UUID proposer = jdbc.queryForObject("SELECT proposed_by FROM medicine_packaging WHERE id = :id",
                new MapSqlParameterSource("id", id), UUID.class);
        if (actorId.equals(proposer)) {
            throw new IllegalArgumentException("A second person must approve packaging");
        }
        jdbc.update("""
                UPDATE medicine_packaging SET status = 'SUPERSEDED'
                WHERE item_id = :itemId AND packaging_unit = :unit AND status = 'APPROVED'
                """, new MapSqlParameterSource().addValue("itemId", proposal.itemId()).addValue("unit", proposal.unit()));
        jdbc.update("""
                UPDATE medicine_packaging SET status = 'APPROVED', approved_by = :actorId, approved_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("actorId", actorId).addValue("id", id));
        audit.record(actorId, "PACKAGING_APPROVE", "MEDICINE_PACKAGING", id, null);
        events.publish("MedicinePackagingApproved", java.util.Map.of("packagingId", id.toString()));
        return new Packaging(id, proposal.itemId(), proposal.unit(), proposal.factor(), proposal.canSplit(),
                proposal.version(), "APPROVED");
    }

    private Packaging map(ResultSet rs, int row) throws SQLException {
        return new Packaging((UUID) rs.getObject("id"), (UUID) rs.getObject("item_id"),
                rs.getString("packaging_unit"), rs.getInt("quantity_in_base_units"),
                rs.getBoolean("can_split"), rs.getInt("version"), rs.getString("status"));
    }

    private String normalize(String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Unit is required");
        }
        return unit.trim().toUpperCase(Locale.ROOT);
    }
}
