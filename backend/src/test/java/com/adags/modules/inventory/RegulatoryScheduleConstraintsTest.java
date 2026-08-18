package com.adags.modules.inventory;

import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V24__regulatory_schedule_enforcement.sql: proves the database triggers
// are a real backstop, independent of ActivateItemServiceTest/
// RestockServiceTest's application-level checks — a raw SQL statement that
// bypasses Item.assertDispensable() entirely must still be rejected (root
// CLAUDE.md "Safety": "Enforce in the repository layer so new endpoints
// inherit it" — the database is the layer no endpoint can route around).
class RegulatoryScheduleConstraintsTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE stock_batch, item, store, supplier CASCADE", new MapSqlParameterSource());
    }

    @Test
    void rejectsActivatingAControlledItemByRawSql() {
        UUID itemId = insertFixtureItem("CONTROLLED", false);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE item SET active = TRUE WHERE id = :id", new MapSqlParameterSource("id", itemId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInsertingAnAlreadyActiveControlledItemByRawSql() {
        assertThatThrownBy(() -> insertFixtureItem("CONTROLLED", true))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void allowsActivatingANonControlledItemByRawSql() {
        UUID itemId = insertFixtureItem("PHARMACY_ONLY_OTC", false);

        jdbcTemplate.update("UPDATE item SET active = TRUE WHERE id = :id", new MapSqlParameterSource("id", itemId));
    }

    @Test
    void rejectsRestockingAControlledItemByRawSql() {
        UUID itemId = insertFixtureItem("CONTROLLED", false);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO stock_batch (item_id, store_id, batch_number, expiry_date,
                                                  unit_cost_amount_minor, supplier_id, delivery_note_ref, received_by)
                        VALUES (:itemId, :storeId, 'BATCH-1', CURRENT_DATE + INTERVAL '1 year', 1000, :supplierId,
                                'DN-1', (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("itemId", itemId)
                        .addValue("storeId", storeId)
                        .addValue("supplierId", supplierId)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertFixtureItem(String regulatorySchedule, boolean active) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO item (code, name, classification, form, unit, regulatory_schedule, active, created_by)
                        VALUES (:code, 'Test Item', 'TEST', 'TABLET', 'TABLET', :schedule, :active,
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("code", "ITEM-" + UUID.randomUUID())
                        .addValue("schedule", regulatorySchedule)
                        .addValue("active", active),
                UUID.class);
    }

    private UUID insertFixtureStore() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO store (name, type, facility_id) VALUES ('Main Pharmacy', 'MAIN_PHARMACY', :facilityId)
                        RETURNING id
                        """,
                new MapSqlParameterSource("facilityId", BOOTSTRAP_FACILITY_ID), UUID.class);
    }

    private UUID insertFixtureSupplier() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO supplier (code, name) VALUES (:code, 'Test Supplier') RETURNING id",
                new MapSqlParameterSource("code", "SUP-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }
}
