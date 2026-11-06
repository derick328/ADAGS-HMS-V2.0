package com.adags.modules.inventory;

import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.application.StockReportService;
import com.adags.modules.inventory.domain.CurrentStockRow;
import com.adags.modules.inventory.domain.ExpiringBatchRow;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.LowStockRow;
import com.adags.modules.inventory.domain.StockValuationRow;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 12-pharmacist.md §6.1: current stock, low-stock alerts, the expiry
// watchlist, and stock valuation — the accountant needs valuation and it
// is otherwise uncomputable (unit_cost lives on the batch, not the item).
class StockReportServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CreateItemService createItemService;
    @Autowired
    private RestockService restockService;
    @Autowired
    private StockReportService stockReportService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE stock_balance, stock_movement, stock_batch, item, store, supplier CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void currentStockListsEveryBatchWithQuantityOnHand() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", 0, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        restockService.restock(new RestockCommand(itemId, storeId, "BATCH-1", LocalDate.now().plusYears(1), 40,
                new Money(1000, "TZS"), supplierId, "DN-1", null, admin));

        List<CurrentStockRow> rows = stockReportService.currentStock();

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.itemId()).isEqualTo(itemId);
            assertThat(row.quantity()).isEqualTo(40);
            assertThat(row.batchNumber()).isEqualTo("BATCH-1");
        });
    }

    @Test
    void lowStockFlagsAnItemBelowItsReorderLevel() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", 50, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        restockService.restock(new RestockCommand(itemId, storeId, "BATCH-1", LocalDate.now().plusYears(1), 10,
                new Money(1000, "TZS"), supplierId, "DN-1", null, admin));

        List<LowStockRow> rows = stockReportService.lowStock();

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.itemId()).isEqualTo(itemId);
            assertThat(row.totalQuantity()).isEqualTo(10);
            assertThat(row.reorderLevel()).isEqualTo(50);
        });
    }

    @Test
    void anItemAtOrAboveItsReorderLevelIsNotFlagged() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", 5, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        restockService.restock(new RestockCommand(itemId, storeId, "BATCH-1", LocalDate.now().plusYears(1), 10,
                new Money(1000, "TZS"), supplierId, "DN-1", null, admin));

        List<LowStockRow> rows = stockReportService.lowStock();

        assertThat(rows).noneMatch(row -> row.itemId().equals(itemId));
    }

    @Test
    void inactiveMedicineRemainsPhysicalButIsNotAvailableOrOnReorderList() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("INACTIVE-AMX", 50, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        restockService.restock(new RestockCommand(itemId, storeId, "BATCH-1",
                LocalDate.now().plusYears(1), 10, new Money(1000, "TZS"),
                supplierId, "DN-1", null, admin));
        jdbcTemplate.update("UPDATE item SET active = FALSE WHERE id = :id",
                new MapSqlParameterSource("id", itemId));

        CurrentStockRow physical = stockReportService.currentStock().stream()
                .filter(row -> row.itemId().equals(itemId)).findFirst().orElseThrow();
        assertThat(physical.quantity()).isEqualTo(10);
        assertThat(physical.stockStatus()).isEqualTo("UNAVAILABLE");
        assertThat(physical.availableQuantity()).isZero();
        assertThat(stockReportService.lowStock()).noneMatch(row -> row.itemId().equals(itemId));
    }

    @Test
    void expiringBatchesRespectsTheProtocolSettableWindow() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", 0, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        restockService.restock(new RestockCommand(itemId, storeId, "NEAR-EXPIRY", LocalDate.now().plusDays(30), 10,
                new Money(1000, "TZS"), supplierId, "DN-1", null, admin));
        restockService.restock(new RestockCommand(itemId, storeId, "FAR-EXPIRY", LocalDate.now().plusYears(2), 10,
                new Money(1000, "TZS"), supplierId, "DN-2", null, admin));

        List<ExpiringBatchRow> rows = stockReportService.expiringBatches();

        assertThat(rows).extracting(ExpiringBatchRow::batchNumber).contains("NEAR-EXPIRY");
        assertThat(rows).extracting(ExpiringBatchRow::batchNumber).doesNotContain("FAR-EXPIRY");
    }

    @Test
    void stockValuationSumsQuantityTimesUnitCostByStore() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem("AMX500", 0, admin);
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        restockService.restock(new RestockCommand(itemId, storeId, "BATCH-1", LocalDate.now().plusYears(1), 20,
                new Money(500, "TZS"), supplierId, "DN-1", null, admin));
        restockService.restock(new RestockCommand(itemId, storeId, "BATCH-2", LocalDate.now().plusMonths(6), 10,
                new Money(800, "TZS"), supplierId, "DN-2", null, admin));

        List<StockValuationRow> rows = stockReportService.stockValuation();

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.storeId()).isEqualTo(storeId);
            assertThat(row.totalValue().amountMinor()).isEqualTo(20L * 500 + 10L * 800);
            assertThat(row.totalValue().currencyCode()).isEqualTo("TZS");
        });
    }

    private UUID createFixtureItem(String code, int reorderLevel, UUID admin) {
        Item item = createItemService.create(new CreateItemCommand(code + "-" + UUID.randomUUID().toString().substring(0, 6),
                "Amoxicillin 500mg capsule", "ANTIBIOTIC", "CAPSULE", "500mg", "CAPSULE", reorderLevel, admin));
        // The low-stock report intentionally excludes inactive catalogue items.
        // Activate the fixture so low-stock assertions exercise quantity, not status.
        jdbcTemplate.update("UPDATE item SET active = TRUE WHERE id = :id",
                new MapSqlParameterSource("id", item.id()));
        return item.id();
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
