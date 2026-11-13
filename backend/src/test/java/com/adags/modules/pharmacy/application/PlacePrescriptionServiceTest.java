package com.adags.modules.pharmacy.application;

import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.inventory.application.ClassifyItemCommand;
import com.adags.modules.inventory.application.ClassifyItemService;
import com.adags.modules.inventory.application.CreateItemCommand;
import com.adags.modules.inventory.application.CreateItemService;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.pharmacy.domain.ItemNotDispensableException;
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

// 14-controlled-medicines-tanzania.md §3: "POST /prescriptions Rejected
// server-side even when a valid item_id is submitted directly."
class PlacePrescriptionServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private CreateItemService createItemService;
    @Autowired
    private ClassifyItemService classifyItemService;
    @Autowired
    private RegisterPatientService registerPatientService;
    @Autowired
    private OpenEncounterService openEncounterService;
    @Autowired
    private PlacePrescriptionService placePrescriptionService;
    @Autowired
    private InventoryFacade inventoryFacade;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE prescription_line, prescription_order, item,
                                 encounter_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void rejectsPrescribingAControlledItem() {
        UUID admin = bootstrapAdminId();
        Item item = createItemService.create(new CreateItemCommand("MOR10", "Morphine sulfate 10mg tablet",
                "OPIOID", "TABLET", "10mg", "TABLET", 0, admin));
        classifyItemService.classify(new ClassifyItemCommand(item.id(), RegulatorySchedule.CONTROLLED,
                "on First Schedule narcotic list", admin));

        Patient patient = registerPatientService.register(patientCommand("Amina", "Hassan", admin));
        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));

        assertThatThrownBy(() -> placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                encounter.id(), item.id(), 10, "AS DIRECTED", false, admin,
                null, null, null, null, null)))
                .isInstanceOf(ItemNotDispensableException.class);
    }

    @Test
    void requiresPregnancyStatusBeforePrescribingForApplicablePatient() {
        UUID admin = bootstrapAdminId();
        UUID itemId = inventoryFacade.ensureCatalogueItem(
                "RX-PREG-" + UUID.randomUUID().toString().substring(0, 8), "Test medicine", "PHARMACY", admin);
        Patient patient = registerPatientService.register(patientCommand("Amina", "Hassan", admin));
        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));

        assertThatThrownBy(() -> placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                encounter.id(), itemId, 1, "As directed", false, admin,
                null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Choose pregnancy status");
    }

    @Test
    void rejectsPrescribingWhenNoUsableStockExists() {
        UUID admin = bootstrapAdminId();
        UUID itemId = inventoryFacade.ensureCatalogueItem(
                "RX-EMPTY-" + UUID.randomUUID().toString().substring(0, 8), "Unstocked medicine", "PHARMACY", admin);
        Patient patient = registerPatientService.register(patientCommand("Amina", "Hassan", admin));
        Encounter encounter = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        jdbcTemplate.update("""
                INSERT INTO consultation_pregnancy_assessment (encounter_id, status, recorded_by)
                VALUES (:encounterId, 'NO', :actorId)
                """, new MapSqlParameterSource().addValue("encounterId", encounter.id()).addValue("actorId", admin));

        assertThatThrownBy(() -> placePrescriptionService.placeOrder(new PlacePrescriptionCommand(
                encounter.id(), itemId, 1, "As directed", false, admin,
                null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No usable, unexpired stock");
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private RegisterPatientCommand patientCommand(String givenName, String familyName, UUID actor) {
        return new RegisterPatientCommand(
                givenName, null, familyName, LocalDate.of(1990, 4, 12), null, null,
                "FEMALE", "SINGLE", null, "+255700" + (100000 + Math.abs((givenName + familyName).hashCode() % 900000)),
                null, null,
                new NextOfKin(null, null, "Next Kin", "SIBLING", null, "+255711000000", false),
                List.of(), null, BOOTSTRAP_FACILITY_ID, actor, null);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
