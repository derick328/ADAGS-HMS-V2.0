package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.ControlledItemBlockedException;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 12-pharmacist.md §7.2 (D3, D6): one restock writes a StockBatch row, one
// StockMovement(RECEIPT) row and a materialised StockBalance — root
// CLAUDE.md "Data": "Stock is an append-only movement ledger. Never a
// mutable quantity column."
class RestockServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RestockService restockService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE stock_balance, stock_movement, stock_batch, item, store, supplier CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void restockWritesABatchAReceiptMovementAndAMaterialisedBalance() {
        UUID itemId = insertFixtureItem();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID actor = bootstrapAdminId();

        StockBatch batch = restockService.restock(new RestockCommand(itemId, storeId, "AMX24071",
                LocalDate.now().plusYears(1), 200, new Money(80000, "TZS"), supplierId, "DN-0001",
                "first delivery", actor));

        assertThat(batch.id()).isNotNull();
        assertThat(batch.batchNumber()).isEqualTo("AMX24071");

        Integer movementCount = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM stock_movement
                        WHERE batch_id = :batchId AND movement_type = 'RECEIPT' AND qty_delta = 200
                        """,
                new MapSqlParameterSource("batchId", batch.id()), Integer.class);
        assertThat(movementCount).isEqualTo(1);

        Integer balance = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", batch.id()), Integer.class);
        assertThat(balance).isEqualTo(200);
    }

    @Test
    void twoRestocksOfTheSameItemAndStoreAccumulateAcrossSeparateBatches() {
        UUID itemId = insertFixtureItem();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID actor = bootstrapAdminId();

        StockBatch first = restockService.restock(new RestockCommand(itemId, storeId, "BATCH-A",
                LocalDate.now().plusMonths(6), 50, new Money(80000, "TZS"), supplierId, "DN-A", null, actor));
        StockBatch second = restockService.restock(new RestockCommand(itemId, storeId, "BATCH-B",
                LocalDate.now().plusMonths(12), 30, new Money(95000, "TZS"), supplierId, "DN-B", null, actor));

        assertThat(first.id()).isNotEqualTo(second.id());

        Integer totalOnHand = jdbcTemplate.queryForObject(
                "SELECT SUM(quantity) FROM stock_balance WHERE item_id = :itemId AND store_id = :storeId",
                new MapSqlParameterSource().addValue("itemId", itemId).addValue("storeId", storeId), Integer.class);
        assertThat(totalOnHand).isEqualTo(80);
    }

    @Test
    void rejectsAPastExpiryDate() {
        UUID itemId = insertFixtureItem();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();

        assertThatThrownBy(() -> restockService.restock(new RestockCommand(itemId, storeId, "EXPIRED-1",
                LocalDate.now().minusDays(1), 10, new Money(1000, "TZS"), supplierId, "DN-X", null,
                bootstrapAdminId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRestockingAControlledItem() {
        UUID itemId = insertFixtureItem("CONTROLLED");
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();

        assertThatThrownBy(() -> restockService.restock(new RestockCommand(itemId, storeId, "BATCH-1",
                LocalDate.now().plusYears(1), 10, new Money(1000, "TZS"), supplierId, "DN-1", null,
                bootstrapAdminId())))
                .isInstanceOf(ControlledItemBlockedException.class);

        Integer batchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_batch WHERE item_id = :itemId",
                new MapSqlParameterSource("itemId", itemId), Integer.class);
        assertThat(batchCount).isEqualTo(0);
    }

    private UUID insertFixtureItem() {
        return insertFixtureItem("UNCLASSIFIED");
    }

    private UUID insertFixtureItem(String regulatorySchedule) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO item (code, name, classification, form, unit, regulatory_schedule, created_by)
                        VALUES (:code, 'Amoxicillin 500mg capsule', 'ANTIBIOTIC', 'CAPSULE', 'CAPSULE', :schedule, :createdBy)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("code", "ITEM-" + UUID.randomUUID())
                        .addValue("schedule", regulatorySchedule)
                        .addValue("createdBy", bootstrapAdminId()),
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
