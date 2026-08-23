package com.adags.modules.surgery;

import com.adags.modules.surgery.application.CompleteChecklistCommand;
import com.adags.modules.surgery.application.CompleteChecklistService;
import com.adags.modules.surgery.application.CompleteSurgeryOrderCommand;
import com.adags.modules.surgery.application.CompleteSurgeryOrderService;
import com.adags.modules.surgery.application.RecordAnaesthesiaCommand;
import com.adags.modules.surgery.application.RecordAnaesthesiaService;
import com.adags.modules.surgery.application.RevokeConsentCommand;
import com.adags.modules.surgery.application.RevokeConsentService;
import com.adags.modules.surgery.application.ScheduleSurgeryCommand;
import com.adags.modules.surgery.application.ScheduleSurgeryService;
import com.adags.modules.surgery.application.StartSurgeryOrderCommand;
import com.adags.modules.surgery.application.StartSurgeryOrderService;
import com.adags.modules.surgery.domain.ChecklistNotSatisfiedException;
import com.adags.modules.surgery.domain.ChecklistPhase;
import com.adags.modules.surgery.domain.ConsentNotObtainedException;
import com.adags.modules.surgery.domain.InvalidSurgeryOrderTransitionException;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryUrgency;
import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 8 items 4/7: the checklist and consent gates, each proven as a
// rejection, not just a happy path — "each pause BLOCKS the next stage."
class SurgeryGatesTest extends IntegrationTestSupport {

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
    private CompleteSurgeryOrderService completeSurgeryOrderService;
    @Autowired
    private RevokeConsentService revokeConsentService;
    @Autowired
    private TheatreRepository theatreRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                TRUNCATE anaesthesia_paper_record, surgical_checklist, procedure_item, surgery_order, theatre,
                         payment_bypass, invoice_line, invoice, encounter, patient CASCADE
                """, new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM price_list_entry WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code = :code)", new MapSqlParameterSource("code", PROCEDURE_CODE));
        jdbcTemplate.update("DELETE FROM service_item WHERE code = :code", new MapSqlParameterSource("code", PROCEDURE_CODE));
    }

    @Test
    void startingWithoutTimeOutIsRejected() {
        SurgeryOrder order = scheduleFixtureOrder();

        assertThatThrownBy(() -> startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), order.scheduledBy())))
                .isInstanceOf(ChecklistNotSatisfiedException.class);
    }

    @Test
    void startingWithRevokedConsentIsRejectedEvenWithTimeOutDone() {
        SurgeryOrder order = scheduleFixtureOrder();
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.TIME_OUT,
                List.of(), null, null, false, order.scheduledBy()));

        revokeConsentService.revoke(new RevokeConsentCommand(order.id(), order.scheduledBy()));

        assertThatThrownBy(() -> startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), order.scheduledBy())))
                .isInstanceOf(ConsentNotObtainedException.class);
    }

    @Test
    void anaesthesiaCannotBeRecordedBeforeSignIn() {
        SurgeryOrder order = scheduleFixtureOrder();

        assertThatThrownBy(() -> recordAnaesthesiaService.record(new RecordAnaesthesiaCommand(
                order.id(), false, "Dr. Mushi", "GENERAL", "Chart-1", order.scheduledBy())))
                .isInstanceOf(ChecklistNotSatisfiedException.class);
    }

    @Test
    void completingWithoutSignOutIsRejected() {
        SurgeryOrder order = scheduleFixtureOrder();
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.TIME_OUT,
                List.of(), null, null, false, order.scheduledBy()));
        startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), order.scheduledBy()));

        assertThatThrownBy(() -> completeSurgeryOrderService.complete(
                new CompleteSurgeryOrderCommand(order.id(), order.scheduledBy())))
                .isInstanceOf(ChecklistNotSatisfiedException.class);
    }

    @Test
    void completingWithSignOutButNoAnaesthesiaRecordIsRejected() {
        SurgeryOrder order = scheduleFixtureOrder();
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.TIME_OUT,
                List.of(), null, null, false, order.scheduledBy()));
        startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), order.scheduledBy()));
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.SIGN_OUT,
                List.of(), true, null, false, order.scheduledBy()));

        assertThatThrownBy(() -> completeSurgeryOrderService.complete(
                new CompleteSurgeryOrderCommand(order.id(), order.scheduledBy())))
                .isInstanceOf(com.adags.modules.surgery.domain.AnaesthesiaNotRecordedException.class);
    }

    @Test
    void aFalseSignOutCountDoesNotSatisfyTheCompletionGate() {
        SurgeryOrder order = scheduleFixtureOrder();
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.SIGN_IN,
                List.of(), null, null, false, order.scheduledBy()));
        recordAnaesthesiaService.record(new RecordAnaesthesiaCommand(
                order.id(), false, "Dr. Mushi", "GENERAL", "Chart-1", order.scheduledBy()));
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.TIME_OUT,
                List.of(), null, null, false, order.scheduledBy()));
        startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), order.scheduledBy()));
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.SIGN_OUT,
                List.of(), false, "recount still short one gauze", false, order.scheduledBy()));

        assertThatThrownBy(() -> completeSurgeryOrderService.complete(
                new CompleteSurgeryOrderCommand(order.id(), order.scheduledBy())))
                .isInstanceOf(ChecklistNotSatisfiedException.class);
    }

    @Test
    void startingATwiceScheduledOrderIsRejected() {
        SurgeryOrder order = scheduleFixtureOrder();
        completeChecklistService.complete(new CompleteChecklistCommand(order.id(), ChecklistPhase.TIME_OUT,
                List.of(), null, null, false, order.scheduledBy()));
        startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), order.scheduledBy()));

        assertThatThrownBy(() -> startSurgeryOrderService.start(new StartSurgeryOrderCommand(order.id(), order.scheduledBy())))
                .isInstanceOf(InvalidSurgeryOrderTransitionException.class);
    }

    private SurgeryOrder scheduleFixtureOrder() {
        UUID admin = bootstrapAdminId();
        seedProcedureCatalogueEntry(admin);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();

        return scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                encounterId, PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.IMMEDIATE, "GENERAL",
                LocalDate.now(), "MORNING", theatreId, 60, List.of(), null,
                true, "s3://consents/abc.pdf", admin));
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
