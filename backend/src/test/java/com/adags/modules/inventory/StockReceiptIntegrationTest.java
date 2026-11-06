package com.adags.modules.inventory;

import com.adags.modules.inventory.api.StockReceiptRequest;
import com.adags.modules.inventory.application.StockReceiptService;
import com.adags.modules.inventory.application.MedicinePackagingService;
import com.adags.modules.inventory.domain.StockViewRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockReceiptIntegrationTest extends IntegrationTestSupport {
    private static final UUID FACILITY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    @Autowired StockReceiptService receipts;
    @Autowired MedicinePackagingService packaging;
    @Autowired InventoryFacade inventory;
    @Autowired StockViewRepository stockViews;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("""
                TRUNCATE stock_adjustment, stock_balance, stock_movement, stock_batch,
                  stock_receipt_line, stock_receipt, medicine_packaging, item, store, supplier CASCADE
                """, new MapSqlParameterSource());
    }

    @Test
    void multiMedicineReceiptConvertsPackagesAndIsIdempotent() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID azuma = item("AZUMA-500", "Azuma 500 mg", "TABLET");
        UUID paracetamol = item("PARA-500", "Paracetamol 500 mg", "TABLET");
        packageRule(azuma, "PACK", 3, true);
        packageRule(paracetamol, "BOX", 100, true);
        var request = request(supplier, store, "delivery-one", List.of(
                line(azuma, "PACK", 10, 10, 0, 3000, "AZ-001"),
                line(paracetamol, "BOX", 4, 4, 0, 12000, "PA-001")));

        var receipt = receipts.confirm(request, actor);
        var retry = receipts.confirm(request, actor);

        assertThat(retry.id()).isEqualTo(receipt.id());
        assertThat(receipt.lines()).hasSize(2);
        assertThat(receipt.lines().get(0).acceptedBaseQuantity()).isEqualTo(30);
        assertThat(receipt.lines().get(1).acceptedBaseQuantity()).isEqualTo(400);
        assertThat(quantity(azuma, "ACTIVE")).isEqualTo(30);
        assertThat(quantity(paracetamol, "ACTIVE")).isEqualTo(400);
        assertThat(receipt.totalAcceptedCostMinor()).isEqualTo(78000);
        assertThat(count("stock_movement")).isEqualTo(2);

        var second = receipts.confirm(request(supplier, store, "delivery-two", List.of(
                line(azuma, "PACK", 10, 10, 0, 3000, "AZ-001"))), actor);
        assertThat(second.id()).isNotEqualTo(receipt.id());
        assertThat(quantity(azuma, "ACTIVE")).isEqualTo(60);
    }

    @Test
    void rejectedGoodsStayQuarantinedAndMissingGoodsNeverEnterStock() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("AZUMA-500", "Azuma", "TABLET");
        packageRule(item, "BOX", 30, true);
        var input = new StockReceiptRequest.Line(item, "BOX", 10, 9, 8, 1,
                "AZ-002", LocalDate.now(FACILITY_ZONE).plusYears(1), 3000,
                "One box damaged; one missing", null);

        var receipt = receipts.confirm(request(supplier, store, "delivery-damaged", List.of(input)), actor);

        assertThat(receipt.lines().getFirst().missingQuantity()).isEqualTo(1);
        assertThat(quantity(item, "ACTIVE")).isEqualTo(240);
        assertThat(quantity(item, "QUARANTINED")).isEqualTo(30);
        assertThat(count("stock_movement")).isEqualTo(2);
        var physical = stockViews.findCurrentStock(List.of(store));
        assertThat(physical).hasSize(2);
        assertThat(physical.stream().filter(row -> row.stockStatus().equals("QUARANTINED"))
                .findFirst().orElseThrow().availableQuantity()).isZero();
    }

    @Test
    void fullyMissingOrderLineCreatesNoPhysicalStock() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("MISSING-ONLY", "Missing item", "TABLET");
        var input = new StockReceiptRequest.Line(item, "TABLET", 5, 0, 0, 0,
                "SUPPLIER-BATCH", LocalDate.now(FACILITY_ZONE).plusYears(1), 100,
                "No goods arrived", null);
        var receipt = receipts.confirm(request(supplier, store, "fully-missing", List.of(input)), actor);
        assertThat(receipt.lines().getFirst().missingQuantity()).isEqualTo(5);
        assertThat(count("stock_batch")).isZero();
        assertThat(count("stock_movement")).isZero();
    }

    @Test
    void unevenPackagePricePreservesExactValuation() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("ODD-COST", "Odd cost tablets", "TABLET");
        packageRule(item, "PACK", 3, true);
        receipts.confirm(request(supplier, store, "delivery-odd", List.of(
                line(item, "PACK", 1, 1, 0, 100, "ODD-1"))), actor);
        Long value = jdbc.queryForObject("""
                SELECT SUM(sb.quantity * b.unit_cost_amount_minor) FROM stock_balance sb
                JOIN stock_batch b ON b.id = sb.batch_id WHERE sb.item_id = :id
                """, new MapSqlParameterSource("id", item), Long.class);
        assertThat(value).isEqualTo(100);
    }

    @Test
    void changingPackageFactorDoesNotRewritePriorReceipt() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("VERSIONED", "Versioned tablets", "TABLET");
        packageRule(item, "PACK", 3, true);
        var old = receipts.confirm(request(supplier, store, "first-version", List.of(
                line(item, "PACK", 1, 1, 0, 3000, "V-1"))), actor);
        var proposal = packaging.propose(item, "PACK", 6, true, "New manufacturer presentation", secondUser());
        packaging.approve(proposal.id(), actor);
        var next = receipts.confirm(request(supplier, store, "second-version", List.of(
                line(item, "PACK", 1, 1, 0, 3000, "V-2"))), actor);

        assertThat(old.lines().getFirst().factor()).isEqualTo(3);
        assertThat(receipts.findById(old.id()).lines().getFirst().factor()).isEqualTo(3);
        assertThat(next.lines().getFirst().factor()).isEqualTo(6);
        assertThat(quantity(item, "ACTIVE")).isEqualTo(9);
    }

    @Test
    void sealedPackageRejectsPartialBaseUnitDispense() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("SEALED", "Sealed presentation", "TABLET");
        packageRule(item, "PACK", 3, false);
        receipts.confirm(request(supplier, store, "sealed-delivery", List.of(
                line(item, "PACK", 2, 2, 0, 3000, "S-1"))), actor);
        UUID batchId = jdbc.queryForObject("SELECT id FROM stock_batch WHERE item_id = :id LIMIT 1",
                new MapSqlParameterSource("id", item), UUID.class);

        assertThatThrownBy(() -> inventory.assertDispensePackaging(item, java.util.Map.of(batchId, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        inventory.assertDispensePackaging(item, java.util.Map.of(batchId, 3));
    }

    @Test
    void invalidSecondLineLeavesNoReceiptOrStock() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("AZUMA-500", "Azuma", "TABLET");
        var valid = line(item, "TABLET", 3, 3, 0, 1000, "AZ-1");
        var invalid = line(UUID.randomUUID(), "TABLET", 2, 2, 0, 1000, "MISSING-ITEM");
        assertThatThrownBy(() -> receipts.confirm(request(supplier, store, "bad-delivery",
                List.of(valid, invalid)), actor)).isInstanceOf(RuntimeException.class);
        assertThat(count("stock_receipt")).isZero();
        assertThat(count("stock_movement")).isZero();
    }

    @Test
    void invalidPackagingExpiredAndControlledMedicinesNeverEnterStock() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("BLOCKED-CASES", "Blocked cases", "TABLET");
        assertThatThrownBy(() -> packaging.propose(item, "PACK", 0, true, "Invalid", actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> receipts.confirm(request(supplier, store, "undefined-package",
                List.of(line(item, "BOX", 1, 1, 0, 100, "B-1"))), actor))
                .isInstanceOf(IllegalArgumentException.class);
        var expired = new StockReceiptRequest.Line(item, "TABLET", null, 1, 1, 0,
                "B-2", LocalDate.now(FACILITY_ZONE), 100, null, null);
        assertThatThrownBy(() -> receipts.confirm(request(supplier, store, "expired",
                List.of(expired)), actor)).isInstanceOf(IllegalArgumentException.class);
        // The database correctly forbids an ACTIVE controlled item. Model a
        // blocked catalogue entry rather than bypassing that existing guard.
        jdbc.update("UPDATE item SET active = FALSE, regulatory_schedule = 'CONTROLLED' WHERE id = :id",
                new MapSqlParameterSource("id", item));
        assertThatThrownBy(() -> receipts.confirm(request(supplier, store, "controlled",
                List.of(line(item, "TABLET", 1, 1, 0, 100, "B-3"))), actor))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("stock_batch")).isZero();
    }

    private StockReceiptRequest request(UUID supplier, UUID store, String key,
                                        List<StockReceiptRequest.Line> lines) {
        LocalDate today = LocalDate.now(FACILITY_ZONE);
        return new StockReceiptRequest(supplier, store, "INV-" + key, "DN-" + key,
                today, today, null, key, lines);
    }

    private StockReceiptRequest.Line line(UUID item, String unit, int received, int accepted,
                                          int rejected, long cost, String batch) {
        return new StockReceiptRequest.Line(item, unit, null, received, accepted, rejected,
                batch, LocalDate.now(FACILITY_ZONE).plusYears(1), cost, null, null);
    }

    private UUID item(String code, String name, String unit) {
        return jdbc.queryForObject("""
                INSERT INTO item (code, name, classification, form, unit, regulatory_schedule,
                                  active, created_by)
                VALUES (:code, :name, 'Pharmaceuticals', 'TABLET', :unit,
                        'PRESCRIPTION_ONLY', TRUE, :actor) RETURNING id
                """, new MapSqlParameterSource().addValue("code", code).addValue("name", name)
                .addValue("unit", unit).addValue("actor", bootstrapAdminId()), UUID.class);
    }

    private UUID store() {
        return jdbc.queryForObject("""
                INSERT INTO store (name, type, facility_id)
                VALUES ('Main Pharmacy', 'MAIN_PHARMACY', :facility) RETURNING id
                """, new MapSqlParameterSource("facility", FACILITY), UUID.class);
    }

    private UUID supplier() {
        return jdbc.queryForObject("""
                INSERT INTO supplier (code, name) VALUES (:code, 'Test supplier') RETURNING id
                """, new MapSqlParameterSource("code", "SUP-" + UUID.randomUUID()), UUID.class);
    }

    private void packageRule(UUID item, String unit, int factor, boolean split) {
        jdbc.update("""
                INSERT INTO medicine_packaging
                  (item_id, packaging_unit, quantity_in_base_units, can_split, version,
                   status, reason, proposed_by, approved_by, approved_at)
                VALUES (:item, :unit, :factor, :split, 1, 'APPROVED', 'Fixture',
                        :proposer, :approver, now())
                """, new MapSqlParameterSource().addValue("item", item).addValue("unit", unit)
                .addValue("factor", factor).addValue("split", split)
                .addValue("proposer", bootstrapAdminId())
                .addValue("approver", secondUser()));
    }

    private UUID secondUser() {
        return jdbc.queryForObject("""
                INSERT INTO app_user (username, password_hash)
                VALUES (:username, 'test-hash') RETURNING id
                """, new MapSqlParameterSource("username", "packaging-test-" + UUID.randomUUID()), UUID.class);
    }

    private int quantity(UUID item, String status) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(sb.quantity), 0) FROM stock_balance sb
                JOIN stock_batch b ON b.id = sb.batch_id
                WHERE sb.item_id = :item AND b.status = :status
                """, new MapSqlParameterSource().addValue("item", item).addValue("status", status), Integer.class);
    }

    private int count(String table) {
        return jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private UUID bootstrapAdminId() {
        return jdbc.getJdbcTemplate().queryForObject(
                "SELECT id FROM app_user ORDER BY created_at ASC LIMIT 1", UUID.class);
    }
}
