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
import java.util.Map;
import java.util.stream.Collectors;

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
                            int version, String status, Integer blistersPerPack, Integer unitsPerBlister) {
    }

    public record PendingPackaging(Packaging packaging, String itemName, String baseUnit,
                                   String reason, String proposedBy) {
    }

    public record CatalogueItem(UUID id, String code, String name, String form, String strength,
                                String baseUnit, boolean active, List<Packaging> packages) {
    }

    public record CataloguePage(List<CatalogueItem> items, boolean hasMore) {
    }

    public CataloguePage catalogue(String query, String configuration, int limit, int offset) {
        String search = query == null ? "" : query.trim();
        var params = new MapSqlParameterSource()
                .addValue("query", "%" + search + "%")
                .addValue("configuration", configuration)
                .addValue("limit", limit + 1)
                .addValue("offset", offset);
        List<CatalogueItem> matches = jdbc.query("""
                SELECT id, code, name, form, strength, unit, active
                FROM item
                WHERE (name ILIKE :query OR code ILIKE :query)
                  AND (:configuration = 'ALL'
                       OR (:configuration = 'CONFIGURED' AND EXISTS (
                           SELECT 1 FROM medicine_packaging p
                           WHERE p.item_id = item.id AND p.status = 'APPROVED'))
                       OR (:configuration = 'UNCONFIGURED' AND NOT EXISTS (
                           SELECT 1 FROM medicine_packaging p
                           WHERE p.item_id = item.id AND p.status = 'APPROVED')))
                ORDER BY name, code, id
                LIMIT :limit OFFSET :offset
                """, params, (rs, row) -> new CatalogueItem(
                (UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("form"), rs.getString("strength"), rs.getString("unit"),
                rs.getBoolean("active"), List.of()));
        boolean hasMore = matches.size() > limit;
        List<CatalogueItem> page = hasMore ? matches.subList(0, limit) : matches;
        if (page.isEmpty()) {
            return new CataloguePage(List.of(), false);
        }
        Map<UUID, List<Packaging>> packages = jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status,
                       blisters_per_pack, units_per_blister
                FROM medicine_packaging
                WHERE item_id IN (:itemIds) AND status = 'APPROVED'
                ORDER BY item_id, packaging_unit
                """, new MapSqlParameterSource("itemIds", page.stream().map(CatalogueItem::id).toList()), this::map)
                .stream().collect(Collectors.groupingBy(Packaging::itemId));
        return new CataloguePage(page.stream().map(item -> new CatalogueItem(
                item.id(), item.code(), item.name(), item.form(), item.strength(), item.baseUnit(),
                item.active(), packages.getOrDefault(item.id(), List.of()))).toList(), hasMore);
    }

    public List<Packaging> approvedForItem(UUID itemId) {
        return jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status,
                       blisters_per_pack, units_per_blister
                FROM medicine_packaging WHERE item_id = :itemId AND status = 'APPROVED'
                ORDER BY packaging_unit
                """, new MapSqlParameterSource("itemId", itemId), this::map);
    }

    public List<Packaging> pending() {
        return jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status,
                       blisters_per_pack, units_per_blister
                FROM medicine_packaging WHERE status = 'PROPOSED' ORDER BY proposed_at
                """, new MapSqlParameterSource(), this::map);
    }

    public List<PendingPackaging> pendingReview() {
        return jdbc.query("""
                SELECT p.id, p.item_id, p.packaging_unit, p.quantity_in_base_units, p.can_split,
                       p.version, p.status, p.blisters_per_pack, p.units_per_blister,
                       p.reason, i.name AS item_name, i.unit AS base_unit, u.username AS proposer_name
                FROM medicine_packaging p
                JOIN item i ON i.id = p.item_id
                JOIN app_user u ON u.id = p.proposed_by
                WHERE p.status = 'PROPOSED'
                ORDER BY p.proposed_at, p.id
                """, new MapSqlParameterSource(), (rs, row) -> new PendingPackaging(
                map(rs, row), rs.getString("item_name"), rs.getString("base_unit"),
                rs.getString("reason"), rs.getString("proposer_name")));
    }

    public Packaging approved(UUID itemId, String unit) {
        Item item = items.findById(itemId).orElseThrow(() -> new NoSuchElementException("Item not found"));
        String normalized = normalize(unit);
        if (normalized.equals(normalize(item.unit()))) {
            return new Packaging(null, itemId, item.unit(), 1, true, 1, "APPROVED", null, null);
        }
        return approvedForItem(itemId).stream()
                .filter(p -> p.unit().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unit is not approved for this medicine"));
    }

    @Transactional
    public Packaging propose(UUID itemId, String unit, int factor, boolean canSplit,
                             String reason, UUID actorId) {
        return propose(itemId, unit, factor, canSplit, reason, actorId, null, null);
    }

    @Transactional
    public Packaging propose(UUID itemId, String unit, int factor, boolean canSplit,
                             String reason, UUID actorId, Integer blistersPerPack, Integer unitsPerBlister) {
        return propose(itemId, unit, factor, canSplit, reason, actorId, blistersPerPack, unitsPerBlister, null);
    }

    @Transactional
    public Packaging propose(UUID itemId, String unit, int factor, boolean canSplit,
                             String reason, UUID actorId, Integer blistersPerPack, Integer unitsPerBlister,
                             UUID replacesPackagingId) {
        Item item = items.findById(itemId).orElseThrow(() -> new NoSuchElementException("Item not found"));
        String normalized = normalize(unit);
        if (normalized.equals(normalize(item.unit())) || factor < 1) {
            throw new IllegalArgumentException("A package must contain at least one base unit and use a distinct unit name");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A packaging reason is required");
        }
        if (blistersPerPack != null || unitsPerBlister != null) {
            if (blistersPerPack == null || unitsPerBlister == null || blistersPerPack <= 0
                    || unitsPerBlister <= 0 || Math.multiplyExact(blistersPerPack, unitsPerBlister) != factor) {
                throw new IllegalArgumentException("Blister hierarchy must match the base-unit conversion");
            }
        }
        // Serialize version allocation and concurrent proposals per product.
        jdbc.queryForObject("SELECT id FROM item WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", itemId), UUID.class);
        if (replacesPackagingId != null) {
            Integer approvedTarget = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM medicine_packaging
                    WHERE id = :id AND item_id = :itemId AND status = 'APPROVED'
                    """, new MapSqlParameterSource().addValue("id", replacesPackagingId)
                    .addValue("itemId", itemId), Integer.class);
            if (approvedTarget == null || approvedTarget != 1) {
                throw new IllegalArgumentException("Select an approved package for this medicine to edit");
            }
            Integer pendingReplacement = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM medicine_packaging
                    WHERE replaces_packaging_id = :id AND status = 'PROPOSED'
                    """, new MapSqlParameterSource("id", replacesPackagingId), Integer.class);
            if (pendingReplacement != null && pendingReplacement > 0) {
                throw new IllegalArgumentException("An edit for this package is already awaiting approval");
            }
        }
        Integer pendingCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM medicine_packaging
                WHERE item_id = :itemId AND packaging_unit = :unit AND status = 'PROPOSED'
                """, new MapSqlParameterSource().addValue("itemId", itemId).addValue("unit", normalized), Integer.class);
        if (pendingCount != null && pendingCount > 0) {
            throw new IllegalArgumentException("A proposal for " + normalized + " is already awaiting approval");
        }
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0) + 1 FROM medicine_packaging
                WHERE item_id = :itemId AND packaging_unit = :unit
                """, new MapSqlParameterSource().addValue("itemId", itemId).addValue("unit", normalized), Integer.class);
        UUID id = jdbc.queryForObject("""
                INSERT INTO medicine_packaging
                  (item_id, packaging_unit, quantity_in_base_units, can_split, version,
                   status, reason, proposed_by, blisters_per_pack, units_per_blister, replaces_packaging_id)
                VALUES (:itemId, :unit, :factor, :canSplit, :version,
                        'PROPOSED', :reason, :actorId, :blisters, :perBlister, :replacesId) RETURNING id
                """, new MapSqlParameterSource().addValue("itemId", itemId).addValue("unit", normalized)
                .addValue("factor", factor).addValue("canSplit", canSplit).addValue("version", next)
                .addValue("reason", reason.trim()).addValue("actorId", actorId)
                .addValue("blisters", blistersPerPack).addValue("perBlister", unitsPerBlister)
                .addValue("replacesId", replacesPackagingId), UUID.class);
        audit.record(actorId, "PACKAGING_PROPOSE", "MEDICINE_PACKAGING", id, null);
        events.publish("MedicinePackagingProposed", java.util.Map.of("packagingId", id.toString()));
        if (blistersPerPack != null && unitsPerBlister > 1 && normalized.startsWith("PACK")) {
            String blisterUnit = "BLISTER_" + unitsPerBlister;
            List<Packaging> existingBlister = jdbc.query("""
                    SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status,
                           blisters_per_pack, units_per_blister
                    FROM medicine_packaging WHERE item_id = :itemId AND packaging_unit = :unit
                      AND status IN ('APPROVED', 'PROPOSED')
                    """, new MapSqlParameterSource().addValue("itemId", itemId)
                    .addValue("unit", blisterUnit), this::map);
            if (existingBlister.stream().anyMatch(p -> p.factor() != unitsPerBlister)) {
                throw new IllegalArgumentException("Existing blister conversion conflicts with the pack hierarchy");
            }
            if (existingBlister.isEmpty()) {
                propose(itemId, blisterUnit, unitsPerBlister, true,
                        "Blister unit for " + normalized + ": " + reason.trim(), actorId);
            }
        }
        return new Packaging(id, itemId, normalized, factor, canSplit, next, "PROPOSED", blistersPerPack, unitsPerBlister);
    }

    @Transactional
    public Packaging approve(UUID id, UUID actorId) {
        List<Packaging> rows = jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status,
                       blisters_per_pack, units_per_blister
                FROM medicine_packaging WHERE id = :id FOR UPDATE
                """, new MapSqlParameterSource("id", id), this::map);
        Packaging proposal = rows.stream().findFirst().orElseThrow(() -> new NoSuchElementException("Packaging proposal not found"));
        if (!proposal.status().equals("PROPOSED")) {
            throw new IllegalArgumentException("Packaging proposal is no longer pending");
        }
        UUID proposer = jdbc.queryForObject("SELECT proposed_by FROM medicine_packaging WHERE id = :id",
                new MapSqlParameterSource("id", id), UUID.class);
        if (actorId.equals(proposer)) {
            throw new IllegalArgumentException("A second person must approve packaging");
        }
        UUID replacesId = jdbc.queryForObject("SELECT replaces_packaging_id FROM medicine_packaging WHERE id = :id",
                new MapSqlParameterSource("id", id), UUID.class);
        if (replacesId != null) {
            Integer replaced = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM medicine_packaging
                    WHERE id = :id AND item_id = :itemId AND status = 'APPROVED'
                    """, new MapSqlParameterSource().addValue("id", replacesId)
                    .addValue("itemId", proposal.itemId()), Integer.class);
            if (replaced == null || replaced != 1) {
                throw new IllegalArgumentException("The package being edited is no longer approved");
            }
        }
        jdbc.update("""
                UPDATE medicine_packaging SET status = 'SUPERSEDED'
                WHERE item_id = :itemId AND status = 'APPROVED'
                  AND (packaging_unit = :unit OR id = :replacesId)
                """, new MapSqlParameterSource().addValue("itemId", proposal.itemId())
                .addValue("unit", proposal.unit()).addValue("replacesId", replacesId));
        jdbc.update("""
                UPDATE medicine_packaging SET status = 'APPROVED', approved_by = :actorId, approved_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("actorId", actorId).addValue("id", id));
        audit.record(actorId, "PACKAGING_APPROVE", "MEDICINE_PACKAGING", id, null);
        events.publish("MedicinePackagingApproved", java.util.Map.of("packagingId", id.toString()));
        return new Packaging(id, proposal.itemId(), proposal.unit(), proposal.factor(), proposal.canSplit(),
                proposal.version(), "APPROVED", proposal.blistersPerPack(), proposal.unitsPerBlister());
    }

    @Transactional
    public Packaging reject(UUID id, String reason, UUID actorId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Enter a reason for rejecting the packaging proposal");
        }
        List<Packaging> rows = jdbc.query("""
                SELECT id, item_id, packaging_unit, quantity_in_base_units, can_split, version, status,
                       blisters_per_pack, units_per_blister
                FROM medicine_packaging WHERE id = :id FOR UPDATE
                """, new MapSqlParameterSource("id", id), this::map);
        Packaging proposal = rows.stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Packaging proposal not found"));
        if (!proposal.status().equals("PROPOSED")) {
            throw new IllegalArgumentException("Packaging proposal is no longer pending");
        }
        UUID proposer = jdbc.queryForObject("SELECT proposed_by FROM medicine_packaging WHERE id = :id",
                new MapSqlParameterSource("id", id), UUID.class);
        if (actorId.equals(proposer)) {
            throw new IllegalArgumentException("A second person must review packaging");
        }
        jdbc.update("""
                UPDATE medicine_packaging
                SET status = 'REJECTED', rejected_by = :actorId, rejected_at = now(), rejection_reason = :reason
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", id).addValue("actorId", actorId)
                .addValue("reason", reason.trim()));
        audit.record(actorId, "PACKAGING_REJECT", "MEDICINE_PACKAGING", id, null);
        events.publish("MedicinePackagingRejected", java.util.Map.of("packagingId", id.toString()));
        return new Packaging(id, proposal.itemId(), proposal.unit(), proposal.factor(), proposal.canSplit(),
                proposal.version(), "REJECTED", proposal.blistersPerPack(), proposal.unitsPerBlister());
    }

    private Packaging map(ResultSet rs, int row) throws SQLException {
        return new Packaging((UUID) rs.getObject("id"), (UUID) rs.getObject("item_id"),
                rs.getString("packaging_unit"), rs.getInt("quantity_in_base_units"),
                rs.getBoolean("can_split"), rs.getInt("version"), rs.getString("status"),
                (Integer) rs.getObject("blisters_per_pack"), (Integer) rs.getObject("units_per_blister"));
    }

    private String normalize(String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Unit is required");
        }
        return unit.trim().toUpperCase(Locale.ROOT);
    }
}
