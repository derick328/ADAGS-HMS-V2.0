package com.adags.modules.inventory;

import com.adags.modules.inventory.application.ClassifyItemCommand;
import com.adags.modules.inventory.application.ClassifyItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.CreateLabStockRequestCommand;
import com.adags.modules.inventory.application.CreateLabStockRequestService;
import com.adags.modules.inventory.application.ReleaseLabStockRequestCommand;
import com.adags.modules.inventory.application.ReleaseLabStockRequestService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.application.ReviewLabStockRequestService;
import com.adags.modules.inventory.domain.BatchExpiredException;
import com.adags.modules.inventory.domain.ControlledItemBlockedException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.LabStockRequest;
import com.adags.modules.inventory.domain.LabStockRequestExceedsRequestedException;
import com.adags.modules.inventory.domain.LabStockRequestNotOpenException;
import com.adags.modules.inventory.domain.LabStockRequestStatus;
import com.adags.modules.inventory.domain.RegulatorySchedule;
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

// 12-pharmacist.md §7/§8: "A release is a transfer, not a dispense" — two
// movement rows, same batch_id, preserving reagent-lot traceability.
class LabStockRequestTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CreateItemService createItemService;
    @Autowired
    private ClassifyItemService classifyItemService;
    @Autowired
    private RestockService restockService;
    @Autowired
    private CreateLabStockRequestService createLabStockRequestService;
    @Autowired
    private ReleaseLabStockRequestService releaseLabStockRequestService;
    @Autowired
    private ReviewLabStockRequestService reviewLabStockRequestService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE lab_stock_request, stock_balance, stock_movement, stock_batch, item, store, supplier CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void releasingInFullTransfersBetweenStoresPreservingTheBatch() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem(admin);
        UUID pharmacyStoreId = insertFixtureStore("MAIN_PHARMACY");
        UUID labStoreId = insertFixtureStore("LAB_SUBSTORE");
        UUID supplierId = insertFixtureSupplier();
        StockBatch batch = restockService.restock(new RestockCommand(itemId, pharmacyStoreId, "REAGENT-LOT-1",
                LocalDate.now().plusYears(1), 50, new Money(1000, "TZS"), supplierId, "DN-1", null, admin));

        LabStockRequest request = createLabStockRequestService.create(
                new CreateLabStockRequestCommand(itemId, 20, labStoreId, admin, null));

        LabStockRequest released = releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(request.id(), batch.id(), 20, admin));

        assertThat(released.status()).isEqualTo(LabStockRequestStatus.RELEASED);
        assertThat(released.remaining()).isZero();

        Integer pharmacyBalance = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId AND store_id = :storeId",
                new MapSqlParameterSource().addValue("batchId", batch.id()).addValue("storeId", pharmacyStoreId), Integer.class);
        Integer labBalance = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId AND store_id = :storeId",
                new MapSqlParameterSource().addValue("batchId", batch.id()).addValue("storeId", labStoreId), Integer.class);
        assertThat(pharmacyBalance).isEqualTo(30);
        assertThat(labBalance).as("same batch_id, now also on hand at the lab store").isEqualTo(20);

        Integer movementCount = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM stock_movement WHERE batch_id = :batchId
                              AND movement_type IN ('TRANSFER_OUT', 'TRANSFER_IN')
                        """,
                new MapSqlParameterSource("batchId", batch.id()), Integer.class);
        assertThat(movementCount).as("two movement rows, not one decrement").isEqualTo(2);
    }

    @Test
    void partialReleaseLeavesTheRequestOpenForAFollowUpRelease() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem(admin);
        UUID pharmacyStoreId = insertFixtureStore("MAIN_PHARMACY");
        UUID labStoreId = insertFixtureStore("LAB_SUBSTORE");
        UUID supplierId = insertFixtureSupplier();
        StockBatch batch = restockService.restock(new RestockCommand(itemId, pharmacyStoreId, "REAGENT-LOT-1",
                LocalDate.now().plusYears(1), 50, new Money(1000, "TZS"), supplierId, "DN-1", null, admin));
        LabStockRequest request = createLabStockRequestService.create(
                new CreateLabStockRequestCommand(itemId, 20, labStoreId, admin, null));

        LabStockRequest firstRelease = releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(request.id(), batch.id(), 12, admin));
        assertThat(firstRelease.status()).isEqualTo(LabStockRequestStatus.PARTIALLY_RELEASED);
        assertThat(firstRelease.remaining()).isEqualTo(8);

        LabStockRequest secondRelease = releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(request.id(), batch.id(), 8, admin));
        assertThat(secondRelease.status()).isEqualTo(LabStockRequestStatus.RELEASED);
    }

    @Test
    void releasingMoreThanRemainingIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem(admin);
        UUID pharmacyStoreId = insertFixtureStore("MAIN_PHARMACY");
        UUID labStoreId = insertFixtureStore("LAB_SUBSTORE");
        UUID supplierId = insertFixtureSupplier();
        StockBatch batch = restockService.restock(new RestockCommand(itemId, pharmacyStoreId, "REAGENT-LOT-1",
                LocalDate.now().plusYears(1), 50, new Money(1000, "TZS"), supplierId, "DN-1", null, admin));
        LabStockRequest request = createLabStockRequestService.create(
                new CreateLabStockRequestCommand(itemId, 20, labStoreId, admin, null));

        assertThatThrownBy(() -> releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(request.id(), batch.id(), 21, admin)))
                .isInstanceOf(LabStockRequestExceedsRequestedException.class);
    }

    @Test
    void rejectingRequiresANote() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem(admin);
        UUID labStoreId = insertFixtureStore("LAB_SUBSTORE");
        LabStockRequest request = createLabStockRequestService.create(
                new CreateLabStockRequestCommand(itemId, 20, labStoreId, admin, null));

        assertThatThrownBy(() -> reviewLabStockRequestService.reject(request.id(), "", admin))
                .isInstanceOf(IllegalArgumentException.class);

        LabStockRequest rejected = reviewLabStockRequestService.reject(request.id(), "out of formulary", admin);
        assertThat(rejected.status()).isEqualTo(LabStockRequestStatus.REJECTED);
    }

    @Test
    void closingAPartiallyReleasedRequestStopsFurtherReleases() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem(admin);
        UUID pharmacyStoreId = insertFixtureStore("MAIN_PHARMACY");
        UUID labStoreId = insertFixtureStore("LAB_SUBSTORE");
        UUID supplierId = insertFixtureSupplier();
        StockBatch batch = restockService.restock(new RestockCommand(itemId, pharmacyStoreId, "REAGENT-LOT-1",
                LocalDate.now().plusYears(1), 50, new Money(1000, "TZS"), supplierId, "DN-1", null, admin));
        LabStockRequest request = createLabStockRequestService.create(
                new CreateLabStockRequestCommand(itemId, 20, labStoreId, admin, null));
        releaseLabStockRequestService.release(new ReleaseLabStockRequestCommand(request.id(), batch.id(), 5, admin));

        LabStockRequest closed = reviewLabStockRequestService.close(request.id(), "no more reagent this month", admin);
        assertThat(closed.status()).isEqualTo(LabStockRequestStatus.CLOSED);

        assertThatThrownBy(() -> releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(request.id(), batch.id(), 5, admin)))
                .isInstanceOf(LabStockRequestNotOpenException.class);
    }

    @Test
    void releasingAnExpiredBatchIsHardBlocked() {
        UUID admin = bootstrapAdminId();
        UUID itemId = createFixtureItem(admin);
        UUID pharmacyStoreId = insertFixtureStore("MAIN_PHARMACY");
        UUID labStoreId = insertFixtureStore("LAB_SUBSTORE");
        UUID supplierId = insertFixtureSupplier();
        UUID batchId = insertExpiredFixtureBatch(itemId, pharmacyStoreId, supplierId, admin);
        LabStockRequest request = createLabStockRequestService.create(
                new CreateLabStockRequestCommand(itemId, 20, labStoreId, admin, null));

        assertThatThrownBy(() -> releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(request.id(), batchId, 5, admin)))
                .isInstanceOf(BatchExpiredException.class);
    }

    @Test
    void releasingAControlledItemIsBlocked() {
        UUID admin = bootstrapAdminId();
        Item item = createItemService.create(new CreateItemCommand("KET50", "Ketamine 50mg/ml injection",
                "ANAESTHETIC", "INJECTION", "50mg/ml", "VIAL", 0, admin));
        UUID pharmacyStoreId = insertFixtureStore("MAIN_PHARMACY");
        UUID labStoreId = insertFixtureStore("LAB_SUBSTORE");
        UUID supplierId = insertFixtureSupplier();
        UUID batchId = insertFixtureBatchBypassingTrigger(item.id(), pharmacyStoreId, supplierId, admin);
        classifyItemService.classify(new ClassifyItemCommand(item.id(), RegulatorySchedule.CONTROLLED,
                "on First Schedule narcotic list", admin));
        LabStockRequest request = createLabStockRequestService.create(
                new CreateLabStockRequestCommand(item.id(), 5, labStoreId, admin, null));

        assertThatThrownBy(() -> releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(request.id(), batchId, 5, admin)))
                .isInstanceOf(ControlledItemBlockedException.class);
    }

    private UUID createFixtureItem(UUID admin) {
        Item item = createItemService.create(new CreateItemCommand("REAGENT-" + UUID.randomUUID().toString().substring(0, 6),
                "Test Reagent", "REAGENT", "LIQUID", null, "ML", 0, admin));
        return item.id();
    }

    private UUID insertFixtureStore(String type) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO store (name, type, facility_id) VALUES (:type, :type, :facilityId) RETURNING id
                        """,
                new MapSqlParameterSource().addValue("type", type).addValue("facilityId", BOOTSTRAP_FACILITY_ID),
                UUID.class);
    }

    private UUID insertFixtureSupplier() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO supplier (code, name) VALUES (:code, 'Test Supplier') RETURNING id",
                new MapSqlParameterSource("code", "SUP-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID insertExpiredFixtureBatch(UUID itemId, UUID storeId, UUID supplierId, UUID admin) {
        UUID batchId = jdbcTemplate.queryForObject("""
                        INSERT INTO stock_batch (item_id, store_id, batch_number, expiry_date, received_at,
                                                  unit_cost_amount_minor, supplier_id, delivery_note_ref, received_by)
                        VALUES (:itemId, :storeId, 'BATCH-EXPIRED', CURRENT_DATE - INTERVAL '1 day',
                                CURRENT_DATE - INTERVAL '2 years', 1000, :supplierId, 'DN-1', :admin)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("itemId", itemId).addValue("storeId", storeId).addValue("supplierId", supplierId)
                        .addValue("admin", admin),
                UUID.class);
        jdbcTemplate.update(
                "INSERT INTO stock_balance (item_id, batch_id, store_id, quantity) VALUES (:itemId, :batchId, :storeId, 50)",
                new MapSqlParameterSource().addValue("itemId", itemId).addValue("batchId", batchId).addValue("storeId", storeId));
        return batchId;
    }

    // trg_block_controlled_item_stock_batch (V24) rejects a raw INSERT into
    // stock_batch for a CONTROLLED item too — this fixture bypasses only
    // the test's own path by inserting the batch BEFORE classifying the
    // item, then the item is classified CONTROLLED afterward, proving the
    // block bites at release-time regardless of when the item became
    // CONTROLLED relative to when its stock was received.
    private UUID insertFixtureBatchBypassingTrigger(UUID itemId, UUID storeId, UUID supplierId, UUID admin) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO stock_batch (item_id, store_id, batch_number, expiry_date,
                                                  unit_cost_amount_minor, supplier_id, delivery_note_ref, received_by)
                        VALUES (:itemId, :storeId, 'BATCH-1', CURRENT_DATE + INTERVAL '1 year', 1000, :supplierId,
                                'DN-1', :admin)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("itemId", itemId).addValue("storeId", storeId).addValue("supplierId", supplierId)
                        .addValue("admin", admin),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
