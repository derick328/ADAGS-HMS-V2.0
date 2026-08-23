package com.adags.modules.surgery;

import com.adags.modules.inventory.application.ActivateItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.surgery.application.AttestProcedureItemCommand;
import com.adags.modules.surgery.application.AttestProcedureItemService;
import com.adags.modules.surgery.application.CompleteSurgeryOrderCommand;
import com.adags.modules.surgery.application.CompleteSurgeryOrderService;
import com.adags.modules.surgery.application.PrepareProcedureItemCommand;
import com.adags.modules.surgery.application.PrepareProcedureItemService;
import com.adags.modules.surgery.application.ReconcileProcedureItemCommand;
import com.adags.modules.surgery.application.ReconcileProcedureItemService;
import com.adags.modules.surgery.domain.InvalidSurgeryOrderTransitionException;
import com.adags.modules.surgery.domain.MissingWastageReasonException;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemAlreadyAttestedException;
import com.adags.modules.surgery.domain.ProcedureItemAlreadyReconciledException;
import com.adags.modules.surgery.domain.ProcedureItemNotBalancedException;
import com.adags.modules.surgery.domain.ProcedureItemNotReconciledException;
import com.adags.modules.surgery.domain.ProcedureItemSelfReconciliationNotAllowedException;
import com.adags.modules.surgery.domain.ProcedurePhase;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.modules.surgery.domain.SurgeryReconciliationIncompleteException;
import com.adags.modules.surgery.domain.SurgeryUrgency;
import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 8 item 3: "Reconciliation gates completion" end to end — nurse
// reconciles, surgeon attests, only then can the order reach COMPLETED.
class ProcedureReconciliationTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private PrepareProcedureItemService prepareProcedureItemService;

    @Autowired
    private ReconcileProcedureItemService reconcileProcedureItemService;

    @Autowired
    private AttestProcedureItemService attestProcedureItemService;

    @Autowired
    private CompleteSurgeryOrderService completeSurgeryOrderService;

    @Autowired
    private TheatreRepository theatreRepository;

    @Autowired
    private SurgeryOrderRepository surgeryOrderRepository;

    @Autowired
    private CreateItemService createItemService;

    @Autowired
    private ActivateItemService activateItemService;

    @Autowired
    private RestockService restockService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                TRUNCATE anaesthesia_paper_record, surgical_checklist, procedure_item, surgery_order, theatre,
                         stock_movement, stock_balance, stock_batch, item, encounter, patient CASCADE
                """, new MapSqlParameterSource());
    }

    // Items 4/7 added further gates ahead of CompleteSurgeryOrderService's
    // own reconciliation check (SIGN_OUT checklist, anaesthesia pointer) —
    // this test suite is about reconciliation specifically, so satisfy
    // those two gates directly rather than re-deriving their own tests
    // here (SurgeryGatesTest covers them).
    private void satisfyChecklistAndAnaesthesiaGates(UUID surgeryOrderId, UUID actor) {
        jdbcTemplate.update("""
                        INSERT INTO surgical_checklist (surgery_order_id, phase, completed_by, count_correct)
                        VALUES (:orderId, 'SIGN_OUT', :actor, TRUE)
                        """,
                new MapSqlParameterSource().addValue("orderId", surgeryOrderId).addValue("actor", actor));
        jdbcTemplate.update("""
                        INSERT INTO anaesthesia_paper_record (surgery_order_id, no_anaesthesia, recorded_by)
                        VALUES (:orderId, TRUE, :actor)
                        """,
                new MapSqlParameterSource().addValue("orderId", surgeryOrderId).addValue("actor", actor));
    }

    @Test
    void reconcilingWithAReturnCreditsTheBatchBackAndFullBalanceCompletesTheOrder() {
        Fixture fx = setUpPreparedItem(5);
        moveToInProgress(fx.order.id());
        UUID nurse = fx.admin;
        UUID surgeon = insertFixtureUser("surgeon1");

        ProcedureItem reconciled = reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 3, 2, 0, null, nurse));
        assertThat(reconciled.quantityUsed()).isEqualTo(3);
        assertThat(reconciled.quantityReturned()).isEqualTo(2);
        assertThat(reconciled.reconciledBy()).isEqualTo(nurse);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", fx.batch.id()), Integer.class);
        // restocked 10, prepare decremented 5 (10 -> 5), then 2 credited back on return (5 -> 7).
        assertThat(remaining).isEqualTo(7);

        Integer returnMovementCount = jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM stock_movement
                        WHERE reference_type = 'PROCEDURE_ITEM_RETURN' AND reference_id = :procedureItemId
                              AND qty_delta = 2
                        """,
                new MapSqlParameterSource("procedureItemId", fx.procedureItem.id()), Integer.class);
        assertThat(returnMovementCount).isEqualTo(1);

        ProcedureItem attested = attestProcedureItemService.attest(
                new AttestProcedureItemCommand(fx.procedureItem.id(), surgeon));
        assertThat(attested.attestedBy()).isEqualTo(surgeon);

        satisfyChecklistAndAnaesthesiaGates(fx.order.id(), surgeon);
        SurgeryOrder completed = completeSurgeryOrderService.complete(
                new CompleteSurgeryOrderCommand(fx.order.id(), surgeon));
        assertThat(completed.status()).isEqualTo(SurgeryOrderStatus.COMPLETED);
    }

    @Test
    void reconcileRejectsAnUnbalancedCount() {
        Fixture fx = setUpPreparedItem(5);

        assertThatThrownBy(() -> reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 3, 1, 0, null, fx.admin)))
                .isInstanceOf(ProcedureItemNotBalancedException.class);
    }

    @Test
    void reconcileRejectsWastageWithNoReason() {
        Fixture fx = setUpPreparedItem(5);

        assertThatThrownBy(() -> reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 3, 0, 2, null, fx.admin)))
                .isInstanceOf(MissingWastageReasonException.class);
    }

    @Test
    void wastageWithAReasonBalancesFine() {
        Fixture fx = setUpPreparedItem(5);

        ProcedureItem reconciled = reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 3, 0, 2, "dropped on the floor, contaminated", fx.admin));

        assertThat(reconciled.quantityWasted()).isEqualTo(2);
        assertThat(reconciled.wastageReason()).isEqualTo("dropped on the floor, contaminated");
    }

    @Test
    void reconcilingTwiceIsRejected() {
        Fixture fx = setUpPreparedItem(5);
        reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 5, 0, 0, null, fx.admin));

        assertThatThrownBy(() -> reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 5, 0, 0, null, fx.admin)))
                .isInstanceOf(ProcedureItemAlreadyReconciledException.class);
    }

    @Test
    void attestingBeforeReconciliationIsRejected() {
        Fixture fx = setUpPreparedItem(5);
        UUID surgeon = insertFixtureUser("surgeon2");

        assertThatThrownBy(() -> attestProcedureItemService.attest(
                new AttestProcedureItemCommand(fx.procedureItem.id(), surgeon)))
                .isInstanceOf(ProcedureItemNotReconciledException.class);
    }

    // root CLAUDE.md task text: "CHECK (reconciled_by != attested_by) —
    // enforce at the service layer."
    @Test
    void theReconcilingNurseCannotAlsoAttest() {
        Fixture fx = setUpPreparedItem(5);
        reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 5, 0, 0, null, fx.admin));

        assertThatThrownBy(() -> attestProcedureItemService.attest(
                new AttestProcedureItemCommand(fx.procedureItem.id(), fx.admin)))
                .isInstanceOf(ProcedureItemSelfReconciliationNotAllowedException.class);
    }

    @Test
    void attestingTwiceIsRejected() {
        Fixture fx = setUpPreparedItem(5);
        UUID surgeon = insertFixtureUser("surgeon3");
        reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 5, 0, 0, null, fx.admin));
        attestProcedureItemService.attest(new AttestProcedureItemCommand(fx.procedureItem.id(), surgeon));

        assertThatThrownBy(() -> attestProcedureItemService.attest(
                new AttestProcedureItemCommand(fx.procedureItem.id(), surgeon)))
                .isInstanceOf(ProcedureItemAlreadyAttestedException.class);
    }

    @Test
    void completingBeforeInProgressIsRejected() {
        Fixture fx = setUpPreparedItem(5); // still SCHEDULED — never moved to IN_PROGRESS

        assertThatThrownBy(() -> completeSurgeryOrderService.complete(
                new CompleteSurgeryOrderCommand(fx.order.id(), fx.admin)))
                .isInstanceOf(InvalidSurgeryOrderTransitionException.class);
    }

    @Test
    void completingWithUnreconciledItemsIsRejected() {
        Fixture fx = setUpPreparedItem(5);
        moveToInProgress(fx.order.id());
        satisfyChecklistAndAnaesthesiaGates(fx.order.id(), fx.admin);
        // never reconciled or attested

        assertThatThrownBy(() -> completeSurgeryOrderService.complete(
                new CompleteSurgeryOrderCommand(fx.order.id(), fx.admin)))
                .isInstanceOf(SurgeryReconciliationIncompleteException.class);
    }

    @Test
    void completingWithReconciledButUnattestedItemsIsRejected() {
        Fixture fx = setUpPreparedItem(5);
        moveToInProgress(fx.order.id());
        satisfyChecklistAndAnaesthesiaGates(fx.order.id(), fx.admin);
        reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                fx.procedureItem.id(), 5, 0, 0, null, fx.admin));

        assertThatThrownBy(() -> completeSurgeryOrderService.complete(
                new CompleteSurgeryOrderCommand(fx.order.id(), fx.admin)))
                .isInstanceOf(SurgeryReconciliationIncompleteException.class);
    }

    @Test
    void anOrderWithNoProcedureItemsCompletesVacuously() {
        UUID admin = bootstrapAdminId();
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);
        moveToInProgress(order.id());
        satisfyChecklistAndAnaesthesiaGates(order.id(), admin);

        SurgeryOrder completed = completeSurgeryOrderService.complete(new CompleteSurgeryOrderCommand(order.id(), admin));

        assertThat(completed.status()).isEqualTo(SurgeryOrderStatus.COMPLETED);
    }

    // backend/CLAUDE.md "Testing": Constraints — Testcontainers against
    // real Postgres. V51's CHECK (quantity_wasted = 0 OR wastage_reason IS
    // NOT NULL), independent of the application-layer
    // MissingWastageReasonException guard.
    @Test
    void dbCheckRejectsWastageWithNoReasonEvenBypassingTheService() {
        Fixture fx = setUpPreparedItem(5);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE procedure_item SET quantity_wasted = 2, quantity_used = 3 WHERE id = :id",
                new MapSqlParameterSource("id", fx.procedureItem.id())))
                .isInstanceOf(DataAccessException.class);
    }

    private void moveToInProgress(UUID surgeryOrderId) {
        jdbcTemplate.update("UPDATE surgery_order SET status = 'IN_PROGRESS' WHERE id = :id",
                new MapSqlParameterSource("id", surgeryOrderId));
    }

    private Fixture setUpPreparedItem(int quantityPrepared) {
        UUID admin = bootstrapAdminId();
        UUID storeId = insertFixtureStore();
        UUID supplierId = insertFixtureSupplier();
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, storeId, supplierId, 10, admin);
        SurgeryOrder order = insertFixtureSurgeryOrder(admin);
        // Deliberately left SCHEDULED here — completingBeforeInProgressIsRejected
        // relies on that. Every other test that needs IN_PROGRESS calls
        // moveToInProgress itself once it's ready to reconcile/complete.

        ProcedureItem prepared = prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.INTRA_OP, quantityPrepared, batch.id(), admin));

        return new Fixture(admin, order, batch, prepared);
    }

    private record Fixture(UUID admin, SurgeryOrder order, StockBatch batch, ProcedureItem procedureItem) {
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
        var item = createItemService.create(new CreateItemCommand("SUTURE-" + UUID.randomUUID().toString().substring(0, 8),
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

    private UUID insertFixtureUser(String username) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash)
                        VALUES (:username, 'x')
                        RETURNING id
                        """,
                new MapSqlParameterSource("username", username + "-" + UUID.randomUUID().toString().substring(0, 6)),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
