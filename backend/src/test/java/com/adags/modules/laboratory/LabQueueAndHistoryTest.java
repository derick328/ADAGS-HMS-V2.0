package com.adags.modules.laboratory;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.application.SubmitTriageAssessmentCommand;
import com.adags.modules.encounters.application.SubmitTriageAssessmentService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.modules.laboratory.application.CollectSpecimenCommand;
import com.adags.modules.laboratory.application.CollectSpecimenService;
import com.adags.modules.laboratory.application.ParameterEntry;
import com.adags.modules.laboratory.application.PlaceLabOrderCommand;
import com.adags.modules.laboratory.application.PlaceLabOrderService;
import com.adags.modules.laboratory.application.RecordLabResultCommand;
import com.adags.modules.laboratory.application.RecordLabResultResult;
import com.adags.modules.laboratory.application.RecordLabResultService;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.Sex;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenQuality;
import com.adags.modules.laboratory.domain.Urgency;
import com.adags.modules.laboratory.domain.VerificationLevel;
import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 15-lab-technician.md §3/§9: the Test Queue and My History read paths this
// build adds on top of the already-tested write paths
// (LabResultIntegrationTest covers record/amend/verify/critical alerting).
// This file proves the new query surface: LabOrderRepository#findOpenOrders,
// BillingFacade#hasActivePaymentBypass, and LabResultRepository's
// entered-by/accession/patient finders.
class LabQueueAndHistoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RegisterPatientService registerPatientService;
    @Autowired
    private OpenEncounterService openEncounterService;
    @Autowired
    private SubmitTriageAssessmentService submitTriageAssessmentService;
    @Autowired
    private PlaceLabOrderService placeLabOrderService;
    @Autowired
    private CollectSpecimenService collectSpecimenService;
    @Autowired
    private RecordLabResultService recordLabResultService;
    @Autowired
    private LabOrderRepository labOrderRepository;
    @Autowired
    private LabResultRepository labResultRepository;
    @Autowired
    private LabTestRepository labTestRepository;
    @Autowired
    private LabTestParameterRepository labTestParameterRepository;
    @Autowired
    private LabReferenceRangeRepository labReferenceRangeRepository;
    @Autowired
    private BillingFacade billingFacade;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE critical_result_notification, lab_result_reagent, lab_result_parameter_value,
                                 lab_result, specimen, lab_order, invoice_line, invoice, payment_bypass,
                                 encounter_event, triage_assessment, vital_reading, vital_signs, allergy_record,
                                 outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM role_assignment WHERE role_id IN "
                        + "(SELECT id FROM role WHERE code = 'DOCTOR')",
                new MapSqlParameterSource());
    }

    @Test
    void findOpenOrdersExcludesOrdersWithAReleasedResult() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        String stillOpenCode = "LAB-QUEUE-OPEN-" + UUID.randomUUID();
        String completedCode = "LAB-QUEUE-DONE-" + UUID.randomUUID();

        Patient patient = registerPatientService.register(patientCommand(admin));
        Encounter encounter = checkInAndTriageImmediate(patient, doctorId);

        LabCatalogueFixture openCatalogue = insertFixtureLabTest(stillOpenCode, 4_000, admin);
        LabOrder openOrder = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                encounter.id(), stillOpenCode, null, Urgency.ROUTINE, doctorId));

        LabCatalogueFixture completedCatalogue = insertFixtureLabTest(completedCode, 4_000, admin);
        LabOrder completedOrder = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                encounter.id(), completedCode, null, Urgency.STAT, doctorId));
        Specimen completedSpecimen = collectSpecimenService.collect(
                new CollectSpecimenCommand(completedOrder.id(), SpecimenQuality.ADEQUATE, null, doctorId));
        recordLabResultService.record(new RecordLabResultCommand(
                completedSpecimen.id(), doctorId, null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(completedCatalogue.parameterId(), 4.2, null, null)), List.of()));

        List<UUID> openOrderIds = labOrderRepository.findOpenOrders().stream().map(LabOrder::id).toList();

        assertThat(openOrderIds).contains(openOrder.id());
        assertThat(openOrderIds).doesNotContain(completedOrder.id());
    }

    // root CLAUDE.md "Billing": IMMEDIATE triage priority creates an active
    // PaymentBypass automatically — the Test Queue's "Emergency" tab signal.
    @Test
    void hasActivePaymentBypassIsTrueForAnImmediateTriagedEncounter() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        Patient patient = registerPatientService.register(patientCommand(admin));
        Encounter encounter = checkInAndTriageImmediate(patient, doctorId);

        assertThat(billingFacade.hasActivePaymentBypass(encounter.id())).isTrue();
        assertThat(billingFacade.hasActivePaymentBypass(UUID.randomUUID())).isFalse();
    }

    @Test
    void myResultsAndPatientLookupFindersReturnTheReleasedResult() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        String testCode = "LAB-HIST-" + UUID.randomUUID();
        Patient patient = registerPatientService.register(patientCommand(admin));
        Encounter encounter = checkInAndTriageImmediate(patient, doctorId);
        LabCatalogueFixture catalogue = insertFixtureLabTest(testCode, 4_000, admin);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                encounter.id(), testCode, null, Urgency.ROUTINE, doctorId));
        Specimen specimen = collectSpecimenService.collect(
                new CollectSpecimenCommand(order.id(), SpecimenQuality.ADEQUATE, null, doctorId));
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                specimen.id(), doctorId, null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(catalogue.parameterId(), 4.2, null, null)), List.of()));

        List<LabResult> mine = labResultRepository.findByEnteredBy(doctorId);
        assertThat(mine).extracting(LabResult::id).contains(recorded.result().id());

        List<LabResult> byAccession = labResultRepository.findByAccessionNo(specimen.accessionNo());
        assertThat(byAccession).extracting(LabResult::id).contains(recorded.result().id());

        List<LabResult> byPatient = labResultRepository.findByPatientId(patient.id());
        assertThat(byPatient).extracting(LabResult::id).contains(recorded.result().id());

        assertThat(labResultRepository.findByAccessionNo("NO-SUCH-ACCESSION")).isEmpty();
        assertThat(labResultRepository.findByPatientId(UUID.randomUUID())).isEmpty();
    }

    private record LabCatalogueFixture(UUID labTestId, UUID parameterId, UUID rangeId) {
    }

    private LabCatalogueFixture insertFixtureLabTest(String code, long priceMinor, UUID createdBy) {
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, :code, 'LAB')",
                new MapSqlParameterSource("code", code));
        UUID serviceItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM service_item WHERE code = :code", new MapSqlParameterSource("code", code), UUID.class);
        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'CASH'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
                        VALUES (:priceListId, :serviceItemId, :amountMinor, :createdBy)
                        """,
                new MapSqlParameterSource()
                        .addValue("priceListId", priceListId)
                        .addValue("serviceItemId", serviceItemId)
                        .addValue("amountMinor", priceMinor)
                        .addValue("createdBy", createdBy));

        LabTest labTest = labTestRepository.insert(LabTest.create(
                code, code, "BLOOD", "EDTA_TUBE", 24, VerificationLevel.NONE, true));
        LabTestParameter parameter = labTestParameterRepository.insert(
                LabTestParameter.create(labTest.id(), "K", "Potassium", "mmol/L", 1, "ISE", "2823-3"));
        LabReferenceRange range = labReferenceRangeRepository.insert(LabReferenceRange.create(
                parameter.id(), 216, null, Sex.ANY, 0.0, 15.0, 3.5, 5.0, 2.5, 6.5, createdBy));
        return new LabCatalogueFixture(labTest.id(), parameter.id(), range.id());
    }

    private Encounter checkInAndTriageImmediate(Patient patient, UUID doctorId) {
        UUID admin = bootstrapAdminId();
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        submitTriageAssessmentService.submit(new SubmitTriageAssessmentCommand(
                checkedIn.id(), checkedIn.version(), doctorId,
                "Fever and headache", "Since yesterday", "NKDA", "None", ModeOfAmbulation.WALKING,
                false, null, null, false, null, false, PregnancyStatus.NO, null,
                TriagePriority.IMMEDIATE, ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING, null,
                null, null,
                36.8, 120, 80, 75, 16, 98, 70, 175, "ALERT", null,
                doctorId, Instant.now()));
        return checkedIn;
    }

    private RegisterPatientCommand patientCommand(UUID actor) {
        String unique = UUID.randomUUID().toString();
        return new RegisterPatientCommand(
                "Test", null, "Patient-" + unique, LocalDate.of(1990, 4, 12), null, null,
                "FEMALE", "SINGLE", null, "+255700" + (100000 + Math.abs(unique.hashCode() % 900000)),
                null, null,
                new NextOfKin(null, null, "Next Kin", "SIBLING", null, "+255711000000", false),
                List.of(), null, BOOTSTRAP_FACILITY_ID, actor, null);
    }

    private UUID insertFixtureDoctor() {
        UUID userId = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash)
                        VALUES (:username, 'x')
                        RETURNING id
                        """,
                new MapSqlParameterSource("username", "test-doctor-" + UUID.randomUUID()), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'DOCTOR'
                        """,
                new MapSqlParameterSource("userId", userId));
        return userId;
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE facility_id = :facilityId LIMIT 1",
                new MapSqlParameterSource("facilityId", BOOTSTRAP_FACILITY_ID), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
