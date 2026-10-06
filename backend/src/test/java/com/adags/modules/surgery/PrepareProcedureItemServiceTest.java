package com.adags.modules.surgery;

import com.adags.modules.inventory.application.ActivateItemService;
import com.adags.modules.inventory.application.ClassifyItemCommand;
import com.adags.modules.inventory.application.ClassifyItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.ControlledItemBlockedException;
import com.adags.modules.inventory.domain.InsufficientStockException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.surgery.application.PrepareProcedureItemCommand;
import com.adags.modules.surgery.application.PrepareProcedureItemService;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedurePhase;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.ProcedureItemStockUnavailableException;
import com.adags.modules.surgery.domain.SurgeryOrderNotOpenException;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.modules.surgery.domain.SurgeryUrgency;
import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 8 item 2: PrepareProcedureItemService against real Postgres — the
// stock decrement, the CONTROLLED block inherited from the repository
// layer, and the surgery-order-must-be-open guard.
class PrepareProcedureItemServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private PrepareProcedureItemService prepareProcedureItemService;

    @Autowired
    private TheatreRepository theatreRepository;

    @Autowired
    private SurgeryOrderRepository surgeryOrderRepository;

    @Autowired
    private CreateItemService createItemService;

    @Autowired
    private ActivateItemService activateItemService;

    @Autowired
    private ClassifyItemService classifyItemService;

    @Autowired
    private RestockService restockService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                TRUNCATE procedure_item, surgery_order, theatre, stock_movement, stock_balance, stock_batch,
                         item_classification_change, item, store, encounter, patient CASCADE
                """, new MapSqlParameterSource());
    }

    @Test
    void preparingAnItemDecrementsStockAndTagsTheMovementToTheProcedureItem() {
        UUID admin = bootstrapAdminId();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, storeId, supplierId, 10, admin);
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);

        ProcedureItem prepared = prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.PRE_OP, 5, batch.id(), admin));

        assertThat(prepared.id()).isNotNull();
        assertThat(prepared.quantityPrepared()).isEqualTo(5);
        assertThat(prepared.quantityUsed()).isZero();

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", batch.id()), Integer.class);
        assertThat(remaining).isEqualTo(5);

        Integer movementCount = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM stock_movement
                        WHERE reference_type = 'PROCEDURE_ITEM' AND reference_id = :procedureItemId
                              AND qty_delta = -5
                        """,
                new MapSqlParameterSource("procedureItemId", prepared.id()), Integer.class);
        assertThat(movementCount).isEqualTo(1);
    }

    // The frontend never sends a batchId — the circulating nurse doesn't type
    // a UUID, so the service resolves the FEFO batch itself. That path (null
    // batchId) was previously untested; every existing test passed an
    // explicit batch. This proves a second prepare against the same item
    // succeeds while stock lasts — the "can't add more than one item" report
    // was NOT a real one-item cap.
    @Test
    void preparingWithoutABatchResolvesFefoAndAllowsMoreThanOneItem() {
        UUID admin = bootstrapAdminId();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, storeId, supplierId, 10, admin);
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);

        ProcedureItem first = prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.PRE_OP, 3, null, admin));
        ProcedureItem second = prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.INTRA_OP, 4, null, admin));

        assertThat(first.batchId()).isEqualTo(batch.id());
        assertThat(second.batchId()).isEqualTo(batch.id());
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", batch.id()), Integer.class);
        assertThat(remaining).isEqualTo(3);
    }

    // The bug behind the "Resource not found" report: with no batchId the
    // FEFO resolver found no batch for an item that was never restocked and
    // threw a bare NoSuchElementException, which the GlobalExceptionHandler
    // renders as a generic 404 "Resource not found." It must be a stock
    // condition instead, so the operator learns to restock.
    @Test
    void preparingAnItemWithNoStockReportsStockUnavailable() {
        UUID admin = bootstrapAdminId();
        insertFixtureStore();
        insertFixtureSupplier();
        UUID itemId = createFixtureItem(admin); // created and activated, but never restocked
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);

        assertThatThrownBy(() -> prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.PRE_OP, 1, null, admin)))
                .isInstanceOf(ProcedureItemStockUnavailableException.class);
    }

    @Test
    void preparingMoreThanAvailableStockIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, storeId, supplierId, 3, admin);
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);

        assertThatThrownBy(() -> prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.INTRA_OP, 10, batch.id(), admin)))
                .isInstanceOf(InsufficientStockException.class);
    }

    // root CLAUDE.md "Safety": CONTROLLED items are blocked from
    // prescribing/dispensing/restock/transfer "in the repository layer so
    // new endpoints inherit it" — this endpoint never mentions CONTROLLED
    // itself, yet the block still fires, because
    // decrementForProcedureItem calls the same Item.assertDispensable()
    // decrementForDispense does.
    @Test
    void preparingAControlledItemIsBlocked() {
        UUID admin = bootstrapAdminId();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, storeId, supplierId, 10, admin);
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);

        classifyItemService.classify(new ClassifyItemCommand(
                itemId, RegulatorySchedule.CONTROLLED, "found on First Schedule narcotic list on review", admin));

        assertThatThrownBy(() -> prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.PRE_OP, 2, batch.id(), admin)))
                .isInstanceOf(ControlledItemBlockedException.class);
    }

    @Test
    void preparingAgainstACancelledSurgeryOrderIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, storeId, supplierId, 10, admin);
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);
        jdbcTemplate.update("UPDATE surgery_order SET status = 'CANCELLED' WHERE id = :id",
                new MapSqlParameterSource("id", order.id()));

        assertThatThrownBy(() -> prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.PRE_OP, 2, batch.id(), admin)))
                .isInstanceOf(SurgeryOrderNotOpenException.class);
    }

    private SurgeryOrder insertFixtureSurgeryOrder(UUID admin) {
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        SurgeryOrder toInsert = SurgeryOrder.schedule(
                encounterId, "APPENDECTOMY", "Appendectomy", "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now().plusDays(1), "MORNING", theatreId, 60, List.of(), null,
                true, "s3://consents/abc.pdf", UUID.randomUUID(), admin);
        return surgeryOrderRepository.insert(toInsert);
    }

    private UUID createFixtureItem(UUID admin) {
        Item item = createItemService.create(new CreateItemCommand("SUTURE-" + UUID.randomUUID().toString().substring(0, 8),
                "Suture 3-0", "CONSUMABLE", "SUTURE", "3-0", "EACH", 20, admin));
        activateItemService.activate(item.id());
        return item.id();
    }

    private StockBatch restock(UUID itemId, UUID storeId, UUID supplierId, int quantity, UUID admin) {
        return restockService.restock(new RestockCommand(itemId, storeId, "BATCH-" + UUID.randomUUID(),
                LocalDate.now().plusYears(1), quantity, new Money(1000, "TZS"), supplierId,
                "DN-" + UUID.randomUUID(), null, admin));
    }

    private UUID insertFixtureStore() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO store (name, type, facility_id) VALUES ('Theatre Store', 'MAIN_PHARMACY', :facilityId)
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

    private UUID insertFixtureEncounter() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);

        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'IN_CONSULT', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
