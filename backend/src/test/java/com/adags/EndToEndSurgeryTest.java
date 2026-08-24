package com.adags;

import com.adags.modules.inventory.application.ActivateItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.application.RestockCommand;
import com.adags.modules.inventory.application.RestockService;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.surgery.application.AttestProcedureItemCommand;
import com.adags.modules.surgery.application.AttestProcedureItemService;
import com.adags.modules.surgery.application.CompleteChecklistCommand;
import com.adags.modules.surgery.application.CompleteChecklistService;
import com.adags.modules.surgery.application.CompleteSurgeryOrderCommand;
import com.adags.modules.surgery.application.CompleteSurgeryOrderService;
import com.adags.modules.surgery.application.PrepareProcedureItemCommand;
import com.adags.modules.surgery.application.PrepareProcedureItemService;
import com.adags.modules.surgery.application.RecordAnaesthesiaCommand;
import com.adags.modules.surgery.application.RecordAnaesthesiaService;
import com.adags.modules.surgery.application.RecordSurgeryDispositionCommand;
import com.adags.modules.surgery.application.RecordSurgeryDispositionService;
import com.adags.modules.surgery.application.ReconcileProcedureItemCommand;
import com.adags.modules.surgery.application.ReconcileProcedureItemService;
import com.adags.modules.surgery.application.ScheduleSurgeryCommand;
import com.adags.modules.surgery.application.ScheduleSurgeryService;
import com.adags.modules.surgery.application.StartSurgeryOrderCommand;
import com.adags.modules.surgery.application.StartSurgeryOrderService;
import com.adags.modules.surgery.domain.AnaesthesiaPaperRecord;
import com.adags.modules.surgery.domain.ChecklistPhase;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedurePhase;
import com.adags.modules.surgery.domain.SurgeryDisposition;
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

// Phase 8, surgery module: the full path that must never break — schedule
// -> SIGN_IN -> anaesthesia -> TIME_OUT -> start (procedure-start billing
// gate + checklist gate) -> prepare -> SIGN_OUT -> reconcile -> attest ->
// complete -> disposition. Same convention as EndToEndBillingTest et al.
// IMMEDIATE urgency throughout so the billing side of this walk is just
// "the PaymentBypass from scheduling already covers procedure start" —
// payment settlement itself is EndToEndBillingTest's job, not this one's.
class EndToEndSurgeryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PROCEDURE_CODE = "APPENDECTOMY";

    @Autowired
    private ScheduleSurgeryService scheduleSurgeryService;
    @Autowired
    private CompleteChecklistService completeChecklistService;
    @Autowired
    private RecordAnaesthesiaService recordAnaesthesiaService;
    @Autowired
    private StartSurgeryOrderService startSurgeryOrderService;
    @Autowired
    private PrepareProcedureItemService prepareProcedureItemService;
    @Autowired
    private ReconcileProcedureItemService reconcileProcedureItemService;
    @Autowired
    private AttestProcedureItemService attestProcedureItemService;
    @Autowired
    private CompleteSurgeryOrderService completeSurgeryOrderService;
    @Autowired
    private RecordSurgeryDispositionService recordSurgeryDispositionService;
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
                TRUNCATE anaesthesia_paper_record, surgical_checklist, procedure_item, surgery_order, theatre,
                         payment_bypass, invoice_line, invoice, stock_movement, stock_balance, stock_batch, item,
                         store, encounter, patient CASCADE
                """, new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM price_list_entry WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code = :code)", new MapSqlParameterSource("code", PROCEDURE_CODE));
        jdbcTemplate.update("DELETE FROM service_item WHERE code = :code", new MapSqlParameterSource("code", PROCEDURE_CODE));
    }

    @Test
    void scheduleThroughCompletionAndRecoveryDischargeDisposition() {
        UUID admin = bootstrapAdminId();
        UUID nurse = insertFixtureUser("nurse");
        UUID surgeon = insertFixtureUser("surgeon");
        seedProcedureCatalogueEntry(80_000, admin);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        UUID itemId = createFixtureItem(admin);
        StockBatch batch = restock(itemId, admin);

        // Schedule — IMMEDIATE, so ScheduleSurgeryService both posts the
        // procedure fee and grants a PaymentBypass for the whole encounter.
        SurgeryOrder order = scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                encounterId, PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.IMMEDIATE, "GENERAL",
                LocalDate.now(), "MORNING", theatreId, 60, List.of(), "urgent appendix",
                true, "s3://consents/abc.pdf", admin));
        assertThat(order.status()).isEqualTo(SurgeryOrderStatus.SCHEDULED);

        // SIGN_IN — required before anaesthesia can be recorded as started.
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.SIGN_IN,
                List.of("patient identity confirmed", "site marked", "allergy check"), null, null, false, nurse));

        AnaesthesiaPaperRecord anaesthesia = recordAnaesthesiaService.record(new RecordAnaesthesiaCommand(
                order.id(), false, "Dr. Mushi", "GENERAL", "Anaesthesia-Chart-0042", surgeon));
        assertThat(anaesthesia.id()).isNotNull();

        // TIME_OUT — required before IN_PROGRESS.
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.TIME_OUT,
                List.of("team introduced", "site/procedure confirmed"), null, null, false, nurse));

        SurgeryOrder started = startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), surgeon));
        assertThat(started.status()).isEqualTo(SurgeryOrderStatus.IN_PROGRESS);

        ProcedureItem prepared = prepareProcedureItemService.prepare(new PrepareProcedureItemCommand(
                order.id(), itemId, ProcedurePhase.INTRA_OP, 5, batch.id(), nurse));

        // SIGN_OUT — count correct, required before COMPLETED.
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.SIGN_OUT,
                List.of("instrument count correct", "specimen labelled"), true, null, false, nurse));

        reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(
                prepared.id(), 3, 2, 0, null, nurse));
        attestProcedureItemService.attest(new AttestProcedureItemCommand(prepared.id(), surgeon));

        SurgeryOrder completed = completeSurgeryOrderService.complete(new CompleteSurgeryOrderCommand(order.id(), surgeon));
        assertThat(completed.status()).isEqualTo(SurgeryOrderStatus.COMPLETED);

        SurgeryOrder dispositioned = recordSurgeryDispositionService.recordDisposition(new RecordSurgeryDispositionCommand(
                order.id(), SurgeryDisposition.RECOVERY_THEN_DISCHARGE, null, surgeon));
        assertThat(dispositioned.disposition()).isEqualTo(SurgeryDisposition.RECOVERY_THEN_DISCHARGE);

        String encounterState = jdbcTemplate.queryForObject(
                "SELECT state FROM encounter WHERE id = :id", new MapSqlParameterSource("id", encounterId), String.class);
        assertThat(encounterState).isEqualTo("CONSULT_COMPLETE");
    }

    private void seedProcedureCatalogueEntry(long amountMinor, UUID admin) {
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, 'Appendectomy', 'PROCEDURE')",
                new MapSqlParameterSource("code", PROCEDURE_CODE));
        UUID serviceItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM service_item WHERE code = :code", new MapSqlParameterSource("code", PROCEDURE_CODE), UUID.class);
        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'CASH'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
                        VALUES (:priceListId, :serviceItemId, :amountMinor, :createdBy)
                        """,
                new MapSqlParameterSource()
                        .addValue("priceListId", priceListId)
                        .addValue("serviceItemId", serviceItemId)
                        .addValue("amountMinor", amountMinor)
                        .addValue("createdBy", admin));
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
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", username + "-" + UUID.randomUUID().toString().substring(0, 6)),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
