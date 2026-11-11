package com.adags.modules.surgery;

import com.adags.modules.surgery.application.ScheduleSurgeryCommand;
import com.adags.modules.surgery.application.ScheduleSurgeryService;
import com.adags.modules.surgery.domain.ConsentNotObtainedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 8 item 1: ScheduleSurgeryService end to end against real Postgres —
// the procedure fee charge, the IMMEDIATE payment bypass, and the
// consent-blocks-scheduling guard (root CLAUDE.md task text).
class ScheduleSurgeryServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PROCEDURE_CODE = "APPENDECTOMY";

    @Autowired
    private ScheduleSurgeryService scheduleSurgeryService;

    @Autowired
    private TheatreRepository theatreRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                TRUNCATE surgery_order, payment_bypass, invoice_line, invoice, theatre, encounter, patient CASCADE
                """, new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM price_list_entry WHERE service_item_id IN "
                + "(SELECT id FROM service_item WHERE code = :code)", new MapSqlParameterSource("code", PROCEDURE_CODE));
        jdbcTemplate.update("DELETE FROM service_item WHERE code = :code", new MapSqlParameterSource("code", PROCEDURE_CODE));
    }

    @Test
    void schedulingAnElectiveSurgeryPostsTheProcedureChargeAndNoBypass() {
        seedProcedureCatalogueEntry(50_000);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        UUID actorId = bootstrapAdminId();

        SurgeryOrder order = scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                encounterId, PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now().plusDays(1), "MORNING", theatreId, 60, List.of(), "Fit for surgery",
                true, "s3://consents/abc.pdf", actorId));

        assertThat(order.id()).isNotNull();
        assertThat(order.procedureName()).isEqualTo("Appendectomy");
        assertThat(order.invoiceLineId()).isNotNull();

        Long patientPortionMinor = jdbcTemplate.queryForObject(
                "SELECT patient_portion_minor FROM invoice_line WHERE id = :id",
                new MapSqlParameterSource("id", order.invoiceLineId()), Long.class);
        assertThat(patientPortionMinor).isEqualTo(50_000L);

        Integer bypassCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_bypass WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(bypassCount).isZero();
    }

    @Test
    void schedulingAnImmediateSurgeryGrantsAnEncounterScopedBypass() {
        seedProcedureCatalogueEntry(50_000);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        UUID actorId = bootstrapAdminId();

        scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                encounterId, PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.IMMEDIATE, "GENERAL",
                LocalDate.now(), "MORNING", theatreId, 60, List.of(), null,
                true, "s3://consents/abc.pdf", actorId));

        Integer activeBypassCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_bypass WHERE encounter_id = :encounterId "
                        + "AND source = 'SURGERY_IMMEDIATE' AND status = 'ACTIVE'",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(activeBypassCount).isEqualTo(1);
    }

    @Test
    void schedulingWithoutConsentIsRejectedAndPostsNoChargeOrClaimsNoSlot() {
        seedProcedureCatalogueEntry(50_000);
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID encounterId = insertFixtureEncounter();
        UUID actorId = bootstrapAdminId();

        assertThatThrownBy(() -> scheduleSurgeryService.schedule(new ScheduleSurgeryCommand(
                encounterId, PROCEDURE_CODE, "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now().plusDays(1), "MORNING", theatreId, 60, List.of(), null,
                false, null, actorId)))
                .isInstanceOf(ConsentNotObtainedException.class);

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surgery_order WHERE encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(orderCount).isZero();

        Integer invoiceLineCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice_line il JOIN invoice i ON i.id = il.invoice_id "
                        + "WHERE i.encounter_id = :encounterId",
                new MapSqlParameterSource("encounterId", encounterId), Integer.class);
        assertThat(invoiceLineCount).isZero();
    }

    private void seedProcedureCatalogueEntry(long amountMinor) {
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
                        .addValue("createdBy", bootstrapAdminId()));
    }

    private UUID insertFixtureEncounter() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
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
