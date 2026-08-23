package com.adags.modules.surgery;

import com.adags.modules.surgery.application.CreateEmergencyDraftSurgeryOrderCommand;
import com.adags.modules.surgery.application.CreateEmergencyDraftSurgeryOrderService;
import com.adags.modules.surgery.application.EmergencyDraftGovernanceService;
import com.adags.modules.surgery.application.ReconcileEmergencyDraftProcedureNameCommand;
import com.adags.modules.surgery.application.ReconcileEmergencyDraftProcedureNameService;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

// Phase 8 item 8: emergency draft mode — no consent gate, no catalogue
// lookup, no charge at creation; reconciled to a catalogue item later,
// which posts the charge and rejoins the normal pipeline; a stale draft
// gets escalated to governance exactly once.
class EmergencyDraftSurgeryOrderTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PROCEDURE_CODE = "APPENDECTOMY";

    @Autowired
    private CreateEmergencyDraftSurgeryOrderService createEmergencyDraftSurgeryOrderService;
    @Autowired
    private ReconcileEmergencyDraftProcedureNameService reconcileEmergencyDraftProcedureNameService;
    @Autowired
    private EmergencyDraftGovernanceService emergencyDraftGovernanceService;
    @Autowired
    private TheatreRepository theatreRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                TRUNCATE surgery_order, theatre, payment_bypass, invoice_line, invoice, encounter, patient CASCADE
                """, new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM price_list_entry WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code = :code)", new MapSqlParameterSource("code", PROCEDURE_CODE));
        jdbcTemplate.update("DELETE FROM service_item WHERE code = :code", new MapSqlParameterSource("code", PROCEDURE_CODE));
    }

    @Test
    void creatingADraftBypassesConsentAndCatalogueAndGrantsABypassButPostsNoCharge() {
        UUID admin = bootstrapAdminId();
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();

        SurgeryOrder draft = createEmergencyDraftSurgeryOrderService.create(new CreateEmergencyDraftSurgeryOrderCommand(
                encounterId, "emergency ex-lap for perforated viscus", "GENERAL_SURGERY", "GENERAL",
                LocalDate.now(), "MORNING", theatreId, 60, List.of(), "brought in unconscious", admin));

        assertThat(draft.status()).isEqualTo(SurgeryOrderStatus.DRAFT_EMERGENCY);
        assertThat(draft.consentObtained()).isFalse();
        assertThat(draft.procedureServiceItemCode()).isNull();
        assertThat(draft.invoiceLineId()).isNull();

        Integer bypassCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_bypass WHERE encounter_id = :id AND source = 'SURGERY_IMMEDIATE' AND status = 'ACTIVE'",
                new MapSqlParameterSource("id", encounterId), Integer.class);
        assertThat(bypassCount).isEqualTo(1);

        Integer invoiceLineCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice_line il JOIN invoice i ON i.id = il.invoice_id WHERE i.encounter_id = :id",
                new MapSqlParameterSource("id", encounterId), Integer.class);
        assertThat(invoiceLineCount).isZero();
    }

    @Test
    void reconcilingTheDraftPostsTheChargeAndMovesToInProgress() {
        UUID admin = bootstrapAdminId();
        seedProcedureCatalogueEntry(admin);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        SurgeryOrder draft = createEmergencyDraftSurgeryOrderService.create(new CreateEmergencyDraftSurgeryOrderCommand(
                encounterId, "emergency ex-lap", "GENERAL_SURGERY", "GENERAL", LocalDate.now(), "MORNING", theatreId,
                60, List.of(), null, admin));

        SurgeryOrder reconciled = reconcileEmergencyDraftProcedureNameService.reconcile(
                new ReconcileEmergencyDraftProcedureNameCommand(draft.id(), PROCEDURE_CODE, admin));

        assertThat(reconciled.status()).isEqualTo(SurgeryOrderStatus.IN_PROGRESS);
        assertThat(reconciled.procedureServiceItemCode()).isEqualTo(PROCEDURE_CODE);
        assertThat(reconciled.procedureName()).isEqualTo("Appendectomy");
        assertThat(reconciled.invoiceLineId()).isNotNull();

        Long patientPortionMinor = jdbcTemplate.queryForObject(
                "SELECT patient_portion_minor FROM invoice_line WHERE id = :id",
                new MapSqlParameterSource("id", reconciled.invoiceLineId()), Long.class);
        assertThat(patientPortionMinor).isEqualTo(80_000L);
    }

    @Test
    void aStaleDraftIsEscalatedOnceAndNotAgainOnASecondSweep() {
        UUID admin = bootstrapAdminId();
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        SurgeryOrder draft = createEmergencyDraftSurgeryOrderService.create(new CreateEmergencyDraftSurgeryOrderCommand(
                encounterId, "emergency ex-lap", "GENERAL_SURGERY", "GENERAL", LocalDate.now(), "MORNING", theatreId,
                60, List.of(), null, admin));
        // Back-date scheduled_at past the 48h governance threshold.
        jdbcTemplate.update("UPDATE surgery_order SET scheduled_at = now() - INTERVAL '49 hours' WHERE id = :id",
                new MapSqlParameterSource("id", draft.id()));

        emergencyDraftGovernanceService.escalateStaleDrafts();
        Integer escalatedAfterFirstSweep = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surgery_order WHERE id = :id AND governance_escalated_at IS NOT NULL",
                new MapSqlParameterSource("id", draft.id()), Integer.class);
        assertThat(escalatedAfterFirstSweep).isEqualTo(1);

        // Status is untouched — "never auto-closed."
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM surgery_order WHERE id = :id", new MapSqlParameterSource("id", draft.id()), String.class);
        assertThat(status).isEqualTo("DRAFT_EMERGENCY");

        // A second sweep does not re-fire (findStaleDrafts excludes already-escalated rows).
        emergencyDraftGovernanceService.escalateStaleDrafts();
        Integer escalatedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surgery_order WHERE id = :id AND governance_escalated_at IS NOT NULL",
                new MapSqlParameterSource("id", draft.id()), Integer.class);
        assertThat(escalatedCount).isEqualTo(1);
    }

    @Test
    void aFreshDraftIsNotEscalated() {
        UUID admin = bootstrapAdminId();
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        SurgeryOrder draft = createEmergencyDraftSurgeryOrderService.create(new CreateEmergencyDraftSurgeryOrderCommand(
                encounterId, "emergency ex-lap", "GENERAL_SURGERY", "GENERAL", LocalDate.now(), "MORNING", theatreId,
                60, List.of(), null, admin));

        emergencyDraftGovernanceService.escalateStaleDrafts();

        Integer escalatedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surgery_order WHERE id = :id AND governance_escalated_at IS NOT NULL",
                new MapSqlParameterSource("id", draft.id()), Integer.class);
        assertThat(escalatedCount).isZero();
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
