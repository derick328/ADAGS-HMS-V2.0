package com.adags.modules.surgery;

import com.adags.modules.inventory.application.ActivateItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.surgery.application.CancelSurgeryOrderCommand;
import com.adags.modules.surgery.application.CancelSurgeryOrderService;
import com.adags.modules.surgery.application.PrepareProcedureItemCommand;
import com.adags.modules.surgery.application.PrepareProcedureItemService;
import com.adags.modules.surgery.application.ScheduleSurgeryCommand;
import com.adags.modules.surgery.application.ScheduleSurgeryService;
import com.adags.modules.surgery.domain.CancellationReason;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedurePhase;
import com.adags.modules.surgery.domain.SurgeryOrder;
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

// Phase 8 item 6: cancellation. The procedure fee is voided (never
// settled in these tests) regardless of reason — all four billing
// branches end in "voided or refunded" for the fee. Unreconciled items
// are swept to quantity_wasted with the cancellation reason.
class CancelSurgeryOrderServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PROCEDURE_CODE = "APPENDECTOMY";

    @Autowired
    private ScheduleSurgeryService scheduleSurgeryService;
    @Autowired
    private CancelSurgeryOrderService cancelSurgeryOrderService;
    @Autowired
    private PrepareProcedureItemService prepareProcedureItemService;
    @Autowired
    private TheatreRepository theatreRepository;
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
                TRUNCATE procedure_item, surgery_order, theatre, payment_bypass, invoice_line, invoice,
                         stock_movement, stock_balance, stock_batch, item, store, encounter, patient CASCADE
                """, new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM price_list_entry WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code = :code)", new MapSqlParameterSource("code", PROCEDURE_CODE));
        jdbcTemplate.update("DELETE FROM service_item WHERE code = :code", new MapSqlParameterSource("code", PROCEDURE_CODE));
    }

    @Test
    void cancellingBeforePrepVoidsTheProcedureFeeAndFreesTheSlot() {
        UUID admin = bootstrapAdminId();
        seedProcedureCatalogueEntry(admin);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        SurgeryOrder order = scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                insertFixtureEncounter(), PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now().plusDays(1), "MORNING", theatreId, 60, List.of(), null,
                true, "s3://consents/abc.pdf", admin));

        SurgeryOrder cancelled = cancelSurgeryOrderService.cancel(
                new CancelSurgeryOrderCommand(order.id(), CancellationReason.NO_SHOW, admin));

        assertThat(cancelled.status()).isEqualTo(SurgeryOrderStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo(CancellationReason.NO_SHOW);

        String invoiceState = jdbcTemplate.queryForObject(
                "SELECT i.state FROM invoice i JOIN invoice_line il ON il.invoice_id = i.id WHERE il.id = :lineId",
                new MapSqlParameterSource("lineId", order.invoiceLineId()), String.class);
        assertThat(invoiceState).isEqualTo("VOID");

        // Cancelling frees the slot — a second order can now claim it.
        SurgeryOrder rebooked = scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                insertFixtureEncounter(), PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now().plusDays(1), "MORNING", theatreId, 60, List.of(), null,
                true, "s3://consents/xyz.pdf", admin));
        assertThat(rebooked.id()).isNotNull();
    }

    @Test
    void cancellingAfterPrepSweepsUnreconciledItemsToWastedWithTheReason() {
        UUID admin = bootstrapAdminId();
        seedProcedureCatalogueEntry(admin);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        SurgeryOrder order = scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                insertFixtureEncounter(), PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now().plusDays(1), "MORNING", theatreId, 60, List.of(), null,
                true, "s3://consents/abc.pdf", admin));
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, admin);
        ProcedureItem prepared = prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.PRE_OP, 4, batch.id(), admin));

        cancelSurgeryOrderService.cancel(new CancelSurgeryOrderCommand(order.id(), CancellationReason.SURGEON_UNAVAILABLE, admin));

        ProcedureItem reconciled = jdbcTemplate.queryForObject(
                "SELECT quantity_used, quantity_returned, quantity_wasted, wastage_reason FROM procedure_item WHERE id = :id",
                new MapSqlParameterSource("id", prepared.id()),
                (rs, rowNum) -> new ProcedureItem(prepared.id(), order.id(), itemId, ProcedurePhase.PRE_OP,
                        prepared.quantityPrepared(), rs.getInt("quantity_used"), rs.getInt("quantity_returned"),
                        rs.getInt("quantity_wasted"), rs.getString("wastage_reason"), batch.id(), admin, null,
                        admin, null, null));

        assertThat(reconciled.quantityUsed()).isZero();
        assertThat(reconciled.quantityReturned()).isZero();
        assertThat(reconciled.quantityWasted()).isEqualTo(4);
        assertThat(reconciled.wastageReason()).contains("SURGEON_UNAVAILABLE");

        // No return movement — the stock is simply gone, not credited back.
        Integer returnMovements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_movement WHERE reference_type = 'PROCEDURE_ITEM_RETURN' AND reference_id = :id",
                new MapSqlParameterSource("id", prepared.id()), Integer.class);
        assertThat(returnMovements).isZero();
    }

    private UUID createFixtureItem(UUID admin) {
        Item item = createItemService.create(new CreateItemCommand("SUTURE-" + UUID.randomUUID().toString().substring(0, 8),
                "Suture 3-0", "CONSUMABLE", "SUTURE", "3-0", "EACH", 20, admin));
        activateItemService.activate(item.id());
        return item.id();
    }

    private StockBatch restock(UUID itemId, UUID admin) {
        UUID storeId = jdbcTemplate.queryForObject("""
                        INSERT INTO store (name, type, facility_id) VALUES ('Theatre Store', 'MAIN_PHARMACY', :facilityId)
                        RETURNING id
                        """,
                new MapSqlParameterSource("facilityId", BOOTSTRAP_FACILITY_ID), UUID.class);
        UUID supplierId = jdbcTemplate.queryForObject(
                "INSERT INTO supplier (code, name) VALUES (:code, 'Test Supplier') RETURNING id",
                new MapSqlParameterSource("code", "SUP-" + UUID.randomUUID().toString().substring(0, 8)), UUID.class);
        return restockService.restock(new RestockCommand(itemId, storeId, "BATCH-" + UUID.randomUUID(),
                LocalDate.now().plusYears(1), 10, new Money(1000, "TZS"), supplierId, "DN-" + UUID.randomUUID(), null, admin));
    }

    private void seedProcedureCatalogueEntry(UUID admin) {
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, 'Appendectomy', 'PROCEDURE')",
                new MapSqlParameterSource("code", PROCEDURE_CODE));
        UUID serviceItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM service_item WHERE code = :code", new MapSqlParameterSource("code", PROCEDURE_CODE), UUID.class);
        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'CASH'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
                        VALUES (:priceListId, :serviceItemId, 80000, :createdBy)
                        """,
                new MapSqlParameterSource()
                        .addValue("priceListId", priceListId)
                        .addValue("serviceItemId", serviceItemId)
                        .addValue("createdBy", admin));
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
