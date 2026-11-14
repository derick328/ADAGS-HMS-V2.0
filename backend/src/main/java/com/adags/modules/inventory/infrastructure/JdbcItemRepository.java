package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcItemRepository implements ItemRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, code, name, classification, form, strength, unit, regulatory_schedule,
                   reorder_level, active, requires_administration_witness, created_by, created_at
            FROM item
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcItemRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Item insert(Item item) {
        String sql = """
                INSERT INTO item (id, code, name, classification, form, strength, unit, regulatory_schedule,
                                   reorder_level, active, requires_administration_witness, created_by)
                VALUES (COALESCE(:id, gen_random_uuid()), :code, :name, :classification, :form, :strength, :unit,
                        :regulatorySchedule, :reorderLevel, :active, :requiresAdministrationWitness, :createdBy)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", item.id(), Types.OTHER)
                .addValue("code", item.code())
                .addValue("name", item.name())
                .addValue("classification", item.classification())
                .addValue("form", item.form())
                .addValue("strength", item.strength(), Types.VARCHAR)
                .addValue("unit", item.unit())
                .addValue("regulatorySchedule", item.regulatorySchedule().name())
                .addValue("reorderLevel", item.reorderLevel())
                .addValue("active", item.active())
                .addValue("requiresAdministrationWitness", item.requiresAdministrationWitness())
                .addValue("createdBy", item.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Item(
                (UUID) rs.getObject("id"), item.code(), item.name(), item.classification(), item.form(),
                item.strength(), item.unit(), item.regulatorySchedule(), item.reorderLevel(), item.active(),
                item.requiresAdministrationWitness(), item.createdBy(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<Item> findById(UUID id) {
        List<Item> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Item> findByCode(String code) {
        List<Item> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE code = :code", new MapSqlParameterSource("code", code), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Item activate(UUID itemId) {
        // trg_block_controlled_item_activation (V24) is the backstop if a
        // caller ever reaches this without first calling
        // Item.assertDispensable() — it raises a plain Postgres exception,
        // not translated here, since that path is a bug elsewhere, not a
        // condition this repository is meant to present nicely.
        String sql = "UPDATE item SET active = TRUE WHERE id = :id RETURNING "
                + "id, code, name, classification, form, strength, unit, regulatory_schedule, "
                + "reorder_level, active, requires_administration_witness, created_by, created_at";
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("id", itemId), this::mapRow);
    }

    @Override
    public Item updateSchedule(UUID itemId, RegulatorySchedule newSchedule) {
        // Reclassifying an already-active item to CONTROLLED also clears
        // active in the same statement — "CONTROLLED is blocked from
        // catalogue activation" (V24) means active and CONTROLLED can never
        // legitimately coexist, and without this, this very UPDATE would
        // trip trg_block_controlled_item_activation itself (NEW.active
        // still true, NEW.regulatory_schedule now CONTROLLED) and reject
        // the reclassification along with the activation it isn't asking for.
        String sql = "UPDATE item SET regulatory_schedule = :schedule, "
                + "active = (active AND :schedule <> 'CONTROLLED') WHERE id = :id RETURNING "
                + "id, code, name, classification, form, strength, unit, regulatory_schedule, "
                + "reorder_level, active, requires_administration_witness, created_by, created_at";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", itemId)
                .addValue("schedule", newSchedule.name());
        return jdbcTemplate.queryForObject(sql, params, this::mapRow);
    }

    @Override
    public Item updateFormAndUnit(UUID itemId, String form, String unit) {
        // A base-unit correction is rare. Serialize it with stock and prescription
        // inserts so the history guard below cannot race a new reference.
        jdbcTemplate.getJdbcTemplate().execute("LOCK TABLE stock_batch, prescription_line, medicine_packaging IN SHARE MODE");
        String sql = "UPDATE item SET form = :form, unit = :unit WHERE id = :id "
                + "AND (UPPER(unit) = :unit OR (NOT EXISTS (SELECT 1 FROM stock_batch WHERE item_id = :id) "
                + "AND NOT EXISTS (SELECT 1 FROM prescription_line WHERE item_id = :id) "
                + "AND NOT EXISTS (SELECT 1 FROM medicine_packaging WHERE item_id = :id AND status <> 'PROPOSED'))) RETURNING "
                + "id, code, name, classification, form, strength, unit, regulatory_schedule, "
                + "reorder_level, active, requires_administration_witness, created_by, created_at";
        var rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("id", itemId).addValue("form", form).addValue("unit", unit), this::mapRow);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Base unit cannot change after packaging, stock or prescriptions exist. Create a distinct catalogue product.");
        }
        return rows.getFirst();
    }

    @Override
    public Item updateAdministrationWitness(UUID itemId, boolean requiresWitness) {
        String sql = "UPDATE item SET requires_administration_witness = :requiresWitness WHERE id = :id RETURNING "
                + "id, code, name, classification, form, strength, unit, regulatory_schedule, "
                + "reorder_level, active, requires_administration_witness, created_by, created_at";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", itemId)
                .addValue("requiresWitness", requiresWitness);
        return jdbcTemplate.queryForObject(sql, params, this::mapRow);
    }

    @Override
    public List<Item> searchByName(String query, int limit) {
        String sql = SELECT_COLUMNS + " WHERE name ILIKE :query OR code ILIKE :query "
                + "ORDER BY CASE WHEN code = :exact THEN 0 ELSE 1 END, name LIMIT :limit";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", "%" + query + "%")
                .addValue("exact", query)
                .addValue("limit", limit);
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    private Item mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Item(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("classification"),
                rs.getString("form"),
                rs.getString("strength"),
                rs.getString("unit"),
                RegulatorySchedule.valueOf(rs.getString("regulatory_schedule")),
                rs.getInt("reorder_level"),
                rs.getBoolean("active"),
                rs.getBoolean("requires_administration_witness"),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
