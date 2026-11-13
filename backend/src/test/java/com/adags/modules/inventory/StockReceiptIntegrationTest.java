package com.adags.modules.inventory;

import com.adags.modules.inventory.api.StockReceiptRequest;
import com.adags.modules.inventory.api.MedicinePurchaseCostController;
import com.adags.shared.authz.Actor;
import com.adags.modules.inventory.application.StockReceiptService;
import com.adags.modules.inventory.application.MedicinePackagingService;
import com.adags.modules.inventory.domain.StockViewRepository;
import com.adags.modules.inventory.domain.ItemRepository;
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
    @Autowired MedicinePurchaseCostController purchaseCosts;
    @Autowired MedicinePackagingService packaging;
    @Autowired InventoryFacade inventory;
    @Autowired StockViewRepository stockViews;
    @Autowired ItemRepository itemRepository;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("""
                TRUNCATE stock_adjustment, stock_balance, stock_movement, stock_batch,
                  stock_receipt_line, stock_receipt, medicine_packaging, item, store, supplier CASCADE
                """, new MapSqlParameterSource());
    }

    @Test
    void medicineCatalogueSearchFindsExactCodeForAdminSettings() {
        UUID item = item("CODE-ONLY-500", "Different display name", "TABLET");
        assertThat(itemRepository.searchByName("CODE-ONLY-500", 20)).extracting(medicine -> medicine.id())
                .contains(item);
    }

    @Test
    void packagingCatalogueIncludesApprovedPackagesAndUnpackagedMedicines() {
        UUID actor = bootstrapAdminId();
        UUID approver = secondUser();
        UUID packaged = item("CAT-PACK", "Catalogue amoxicillin", "TABLET");
        item("CAT-BARE", "Catalogue paracetamol", "TABLET");
        var proposed = packaging.propose(packaged, "PACK_10", 10, true,
                "One blister of ten", actor, 1, 10);
        packaging.approve(proposed.id(), approver);

        var result = packaging.catalogue("Catalogue", "ALL", 50, 0);

        assertThat(result.hasMore()).isFalse();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().stream().filter(row -> row.id().equals(packaged)).findFirst().orElseThrow()
                .packages()).extracting(MedicinePackagingService.Packaging::unit).contains("PACK_10");
        assertThat(result.items().stream().filter(row -> row.code().equals("CAT-BARE")).findFirst().orElseThrow()
                .packages()).isEmpty();
        assertThat(packaging.catalogue("Catalogue", "ALL", 1, 0).hasMore()).isTrue();
        assertThat(packaging.catalogue("Catalogue", "CONFIGURED", 50, 0).items())
                .extracting(MedicinePackagingService.CatalogueItem::id).containsExactly(packaged);
        assertThat(packaging.catalogue("Catalogue", "UNCONFIGURED", 50, 0).items())
                .extracting(MedicinePackagingService.CatalogueItem::code).containsExactly("CAT-BARE");
        assertThat(packaging.catalogue("CAT-PACK", "ALL", 50, 0).items())
                .extracting(MedicinePackagingService.CatalogueItem::id).containsExactly(packaged);
    }

    @Test
    void adminDefaultPurchaseCostChangesDoNotRewriteSupplierReceipt() {
        UUID actorId = bootstrapAdminId();
        Actor admin = new Actor(actorId);
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("DEFAULT-COST", "Default cost tablets", "TABLET");
        packageRule(item, "PACK", 50, true);
        purchaseCosts.update(item, new MedicinePurchaseCostController.Update("PACK", 3000L), admin);
        var receipt = receipts.confirm(request(supplier, store, "supplier-cost", List.of(
                line(item, "PACK", 2, 2, 0, 2800, "DC-1"))), actorId);
        purchaseCosts.update(item, new MedicinePurchaseCostController.Update("PACK", 3500L), admin);

        assertThat(purchaseCosts.current(item, admin).amountMinor()).isEqualTo(3500L);
        assertThat(purchaseCosts.history(item, admin)).hasSize(2);
        assertThat(receipts.findById(receipt.id()).lines().getFirst().unitPurchaseCostMinor()).isEqualTo(2800L);
        assertThat(receipts.findById(receipt.id()).totalAcceptedCostMinor()).isEqualTo(5600L);
    }

    @Test
    void blisterHierarchyAndTwoPackSizesUseTheirOwnApprovedFactors() {
        UUID actor = bootstrapAdminId();
        UUID approver = secondUser();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("PARA-VARIANTS", "Paracetamol variants", "TABLET");
        var pack50 = packaging.propose(item, "PACK_50", 50, true, "Five blisters", actor, 5, 10);
        var pack100 = packaging.propose(item, "PACK_100", 100, true, "Ten blisters", actor, 10, 10);
        var blister = packaging.pending().stream()
                .filter(p -> p.itemId().equals(item) && p.unit().equals("BLISTER_10"))
                .findFirst().orElseThrow();
        packaging.approve(pack50.id(), approver);
        packaging.approve(pack100.id(), approver);
        packaging.approve(blister.id(), approver);
        var countedPack = new StockReceiptRequest.Line(item, "PACK_50", null, 10, 10, 0,
                "PV-1", LocalDate.now(FACILITY_ZONE).plusYears(1), 3000L, null, null, 5, 10);
        var receipt = receipts.confirm(request(supplier, store, "multiple-packs", List.of(
                countedPack,
                line(item, "BLISTER_10", 2, 2, 0, 600, "PV-2"),
                line(item, "TABLET", 5, 5, 0, 60, "PV-3"),
                line(item, "PACK_100", 1, 1, 0, 5000, "PV-4"))), actor);
        assertThat(receipt.lines().stream().map(StockReceiptService.LineResult::acceptedBaseQuantity))
                .containsExactly(500, 20, 5, 100);
        assertThat(packaging.approved(item, "PACK_50").blistersPerPack()).isEqualTo(5);
        assertThat(packaging.approved(item, "PACK_100").unitsPerBlister()).isEqualTo(10);
        assertThat(receipt.lines().getFirst().blistersPerPack()).isEqualTo(5);
        assertThat(receipt.lines().getFirst().unitsPerBlister()).isEqualTo(10);
        assertThat(quantity(item, "ACTIVE")).isEqualTo(625);
        var wrongCount = new StockReceiptRequest.Line(item, "PACK_50", null, 1, 1, 0,
                "PV-WRONG", LocalDate.now(FACILITY_ZONE).plusYears(1), 3000L, null, null, 10, 5);
        assertThatThrownBy(() -> receipts.confirm(request(supplier, store, "wrong-count", List.of(wrongCount)), actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blister counts");
    }

    @Test
    void singleUnitBlisterPackCanBeApprovedAndReceived() {
        UUID actor = bootstrapAdminId();
        UUID approver = secondUser();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("SINGLE-BLISTER", "Single dose tablets", "TABLET");
        var proposed = packaging.propose(item, "PACK_1", 1, true, "One tablet in one blister", actor, 1, 1);
        packaging.approve(proposed.id(), approver);

        var countedPack = new StockReceiptRequest.Line(item, "PACK_1", null, 2, 2, 0,
                "SINGLE-1", LocalDate.now(FACILITY_ZONE).plusYears(1), 100L, null, null, 1, 1);
        var receipt = receipts.confirm(request(supplier, store, "single-unit-pack", List.of(countedPack)), actor);

        assertThat(packaging.approved(item, "PACK_1").factor()).isEqualTo(1);
        assertThat(receipt.lines().getFirst().acceptedBaseQuantity()).isEqualTo(2);
        assertThat(quantity(item, "ACTIVE")).isEqualTo(2);
    }

    @Test
    void duplicatePendingPackagingProposalHasActionableError() {
        UUID actor = bootstrapAdminId();
        UUID item = item("PENDING-PACK", "Pending pack tablets", "TABLET");
        packaging.propose(item, "PACK_1", 1, true, "One tablet", actor, 1, 1);

        assertThatThrownBy(() -> packaging.propose(item, "PACK_1", 1, true,
                "Same pack again", actor, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already awaiting approval");
    }

    @Test
    void editedPackageKeepsOldDefinitionUntilApprovalThenReplacesIt() {
        UUID proposer = bootstrapAdminId();
        UUID reviewer = secondUser();
        UUID item = item("EDIT-PACK", "Editable pack tablets", "TABLET");
        var original = packaging.propose(item, "PACK_50", 50, true,
                "Original manufacturer count", proposer, 5, 10);
        packaging.approve(original.id(), reviewer);

        var edit = packaging.propose(item, "PACK_60", 60, false,
                "Manufacturer changed to six blisters", proposer, 6, 10, original.id());
        assertThat(packaging.approvedForItem(item)).extracting(MedicinePackagingService.Packaging::unit)
                .contains("PACK_50").doesNotContain("PACK_60");
        assertThatThrownBy(() -> packaging.propose(item, "PACK_60", 60, false,
                "Duplicate edit", proposer, 6, 10, original.id()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already awaiting approval");
        assertThatThrownBy(() -> packaging.approve(edit.id(), proposer))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("second person");

        packaging.approve(edit.id(), reviewer);
        assertThat(packaging.approvedForItem(item)).extracting(MedicinePackagingService.Packaging::unit)
                .contains("PACK_60").doesNotContain("PACK_50");
        assertThat(jdbc.queryForObject("SELECT status FROM medicine_packaging WHERE id = :id",
                new MapSqlParameterSource("id", original.id()), String.class)).isEqualTo("SUPERSEDED");
    }

    @Test
    void secondReviewerCanRejectPackagingAndProposerCanResubmit() {
        UUID proposer = bootstrapAdminId();
        UUID reviewer = secondUser();
        UUID item = item("REJECT-PACK", "Rejected pack tablets", "TABLET");
        var proposal = packaging.propose(item, "PACK_3", 3, true,
                "Three tablets per pack", proposer, 1, 3);

        var pending = packaging.pendingReview().stream()
                .filter(row -> row.packaging().id().equals(proposal.id())).findFirst().orElseThrow();
        assertThat(pending.itemName()).isEqualTo("Rejected pack tablets");
        assertThat(pending.reason()).isEqualTo("Three tablets per pack");
        assertThatThrownBy(() -> packaging.reject(proposal.id(), "Incorrect manufacturer count", proposer))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("second person");

        assertThat(packaging.reject(proposal.id(), "Incorrect manufacturer count", reviewer).status())
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM medicine_packaging WHERE id = :id",
                new MapSqlParameterSource("id", proposal.id()), String.class))
                .isEqualTo("Incorrect manufacturer count");
        assertThat(packaging.propose(item, "PACK_3", 3, true,
                "Corrected count", proposer, 1, 3).version()).isEqualTo(2);
    }

    @Test
    void unknownPurchaseCostRemainsUnknownAndDoesNotRepriceStockReceipt() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("UNCOSTED-500", "Uncosted tablets", "TABLET");
        packageRule(item, "PACK", 50, true);
        var input = new StockReceiptRequest.Line(item, "PACK", null, 10, 10, 0,
                "UC-1", LocalDate.now(FACILITY_ZONE).plusYears(1), null, null, null);
        var receipt = receipts.confirm(request(supplier, store, "uncosted", List.of(input)), actor);

        assertThat(receipt.lines().getFirst().acceptedBaseQuantity()).isEqualTo(500);
        assertThat(receipt.lines().getFirst().unitPurchaseCostMinor()).isNull();
        assertThat(receipt.totalAcceptedCostMinor()).isNull();
        assertThat(quantity(item, "ACTIVE")).isEqualTo(500);
        Boolean known = jdbc.queryForObject("SELECT cost_known FROM stock_batch WHERE item_id = :id",
                new MapSqlParameterSource("id", item), Boolean.class);
        assertThat(known).isFalse();
        assertThat(receipts.confirm(request(supplier, store, "uncosted", List.of(input)), actor).id())
                .isEqualTo(receipt.id());
        assertThat(quantity(item, "ACTIVE")).isEqualTo(500);
    }

    @Test
    void impossibleReceivingQuantitiesAreRejectedBeforeStockMutation() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("BAD-RECEIPT", "Bad receipt", "TABLET");
        var input = new StockReceiptRequest.Line(item, "TABLET", null, 11, 20, 22,
                "BAD-1", LocalDate.now(FACILITY_ZONE).plusYears(1), null, "Damaged", null);
        assertThatThrownBy(() -> receipts.confirm(request(supplier, store, "bad-quantity", List.of(input)), actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("stock_receipt")).isZero();
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
                "AZ-002", LocalDate.now(FACILITY_ZONE).plusYears(1), 3000L,
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
                "SUPPLIER-BATCH", LocalDate.now(FACILITY_ZONE).plusYears(1), 100L,
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
    void sealedPackageWithIndivisibleCostIsRejectedBeforeItCreatesUnusableCostTranches() {
        UUID actor = bootstrapAdminId();
        UUID store = store();
        UUID supplier = supplier();
        UUID item = item("SEALED-ODD", "Sealed odd cost", "TABLET");
        packageRule(item, "PACK", 3, false);
        assertThatThrownBy(() -> receipts.confirm(request(supplier, store, "sealed-odd", List.of(
                line(item, "PACK", 1, 1, 0, 100, "S-ODD"))), actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("stock_batch")).isZero();
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
                "B-2", LocalDate.now(FACILITY_ZONE), 100L, null, null);
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
