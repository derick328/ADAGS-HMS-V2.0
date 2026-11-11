package com.adags.modules.inpatient;

import com.adags.modules.clinical.application.AddDiagnosisService;
import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.inpatient.application.AdmitPatientCommand;
import com.adags.modules.inpatient.application.AdmitPatientService;
import com.adags.modules.inpatient.application.AssignBedCommand;
import com.adags.modules.inpatient.application.AssignBedService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.BedStatus;
import com.adags.modules.inpatient.domain.MissingPlacementReasonException;
import com.adags.modules.inpatient.domain.PlacementReason;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 6 item 1, end to end against the real encounter state machine:
// IN_CONSULT -> ADMITTED (AdmitPatientService) -> IN_WARD (AssignBedService,
// first bed only). 07-doctor.md §7's ADMITTED disposition, 17-ward-nurse.md
// §1/§8.
class AdmissionLifecycleIntegrationTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private AdmitPatientService admitPatientService;

    @Autowired
    private AssignBedService assignBedService;

    @Autowired
    private AdmissionRepository admissionRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BedAssignmentRepository bedAssignmentRepository;

    @Autowired
    private ConsultationRecordRepository consultationRecordRepository;

    @Autowired
    private AddDiagnosisService addDiagnosisService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE bed_assignment, admission, bed, ward, diagnosis, consultation_record,
                                 encounter_event, encounter, patient, icd10_code CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void admitTransitionsInConsultToAdmittedAndCreatesTheAdmissionRow() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        fillAndDiagnose(encounterId, doctorId);

        Admission admission = admitPatientService.admit(
                new AdmitPatientCommand(encounterId, doctorId, WardClass.GENERAL, false, null));

        assertThat(admission.id()).isNotNull();
        assertThat(admission.orderedWardClass()).isEqualTo(WardClass.GENERAL);
        assertThat(currentState(encounterId)).isEqualTo("ADMITTED");
        assertThat(admissionRepository.findByEncounterId(encounterId)).isPresent();
    }

    @Test
    void admitOnAnEncounterNotInConsultIsRejected() {
        UUID encounterId = insertFixtureEncounter("AWAITING_CONSULT");

        assertThatThrownBy(() -> admitPatientService.admit(
                new AdmitPatientCommand(encounterId, bootstrapAdminId(), WardClass.GENERAL, false, null)))
                .isInstanceOf(InvalidEncounterTransitionException.class);
    }

    @Test
    void aSecondAdmissionForTheSameEncounterIsRejected() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        fillAndDiagnose(encounterId, doctorId);
        admitPatientService.admit(new AdmitPatientCommand(encounterId, doctorId, WardClass.GENERAL, false, null));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO admission (encounter_id, patient_id, facility_id, admitting_doctor_id,
                                                ordered_ward_class, admitted_at)
                        SELECT encounter_id, patient_id, facility_id, admitting_doctor_id, ordered_ward_class, now()
                        FROM admission WHERE encounter_id = :encounterId
                        """,
                new MapSqlParameterSource("encounterId", encounterId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void firstBedAssignmentMovesTheEncounterToInWard() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        fillAndDiagnose(encounterId, doctorId);
        Admission admission = admitPatientService.admit(
                new AdmitPatientCommand(encounterId, doctorId, WardClass.GENERAL, false, null));
        UUID bedId = insertFixtureBed(WardClass.GENERAL);

        BedAssignment assignment = assignBedService.assign(
                new AssignBedCommand(admission.id(), bedId, null, doctorId));

        assertThat(assignment.placedAboveOrderedClass()).isFalse();
        assertThat(currentState(encounterId)).isEqualTo("IN_WARD");
    }

    @Test
    void transferDoesNotRepeatTheEncounterTransition() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        fillAndDiagnose(encounterId, doctorId);
        Admission admission = admitPatientService.admit(
                new AdmitPatientCommand(encounterId, doctorId, WardClass.GENERAL, false, null));
        UUID firstBed = insertFixtureBed(WardClass.GENERAL);
        assignBedService.assign(new AssignBedCommand(admission.id(), firstBed, null, doctorId));
        UUID secondBed = insertFixtureBed(WardClass.GENERAL);

        BedAssignment transfer = assignBedService.assign(new AssignBedCommand(admission.id(), secondBed, null, doctorId));

        assertThat(currentState(encounterId)).isEqualTo("IN_WARD");
        assertThat(bedAssignmentRepository.findActiveByAdmissionId(admission.id()).orElseThrow().bedId())
                .isEqualTo(transfer.bedId());
        assertThat(bedAssignmentRepository.findByAdmissionId(admission.id())).hasSize(2);
    }

    // 17-ward-nurse.md §1 ⚠2: assigning into a higher-rated ward than the
    // doctor ordered without a reason is rejected end to end, not only at
    // the domain factory (BedAssignmentTest already covers that in isolation).
    @Test
    void assigningAboveOrderedClassWithNoReasonIsRejected() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        fillAndDiagnose(encounterId, doctorId);
        Admission admission = admitPatientService.admit(
                new AdmitPatientCommand(encounterId, doctorId, WardClass.GENERAL, false, null));
        UUID privateBed = insertFixtureBed(WardClass.PRIVATE);

        assertThatThrownBy(() -> assignBedService.assign(new AssignBedCommand(admission.id(), privateBed, null, doctorId)))
                .isInstanceOf(MissingPlacementReasonException.class);
        assertThat(currentState(encounterId)).isEqualTo("ADMITTED");
    }

    @Test
    void assigningAboveOrderedClassWithAReasonMovesToInWard() {
        UUID encounterId = insertFixtureEncounter("IN_CONSULT");
        UUID doctorId = bootstrapAdminId();
        fillAndDiagnose(encounterId, doctorId);
        Admission admission = admitPatientService.admit(
                new AdmitPatientCommand(encounterId, doctorId, WardClass.GENERAL, false, null));
        UUID privateBed = insertFixtureBed(WardClass.PRIVATE);

        BedAssignment assignment = assignBedService.assign(
                new AssignBedCommand(admission.id(), privateBed, PlacementReason.NO_BED_IN_ORDERED_CLASS, doctorId));

        assertThat(assignment.placedAboveOrderedClass()).isTrue();
        assertThat(currentState(encounterId)).isEqualTo("IN_WARD");
    }

    private void fillAndDiagnose(UUID encounterId, UUID doctorId) {
        UUID consultationRecordId = consultationRecordRepository.insert(
                ConsultationRecord.open(encounterId, doctorId)).id();
        consultationRecordRepository.autosave(consultationRecordId, "Fever for 3 days", null, null, null, null,
                "Alert, mild pallor", null, "Awaiting malaria RDT");
        jdbcTemplate.update("""
                        INSERT INTO icd10_code (code, title, icd10_release) VALUES ('B50.9', 'P. falciparum malaria, unspecified', '2019')
                        ON CONFLICT (code) DO NOTHING
                        """, new MapSqlParameterSource());
        addDiagnosisService.addDiagnosis(encounterId, consultationRecordId, "B50.9", null,
                DiagnosisCertainty.PROVISIONAL, true, doctorId);
    }

    private UUID insertFixtureBed(WardClass wardClass) {
        UUID departmentId = insertFixtureDepartment();
        Ward ward = wardRepository.insert(new Ward(null, BOOTSTRAP_FACILITY_ID, departmentId,
                "W-" + UUID.randomUUID().toString().substring(0, 6), "Test Ward", wardClass, null,
                new Money(10000, "TZS"), true, null));
        return bedRepository.insert(new Bed(null, ward.id(), "Bed 1", "STANDARD", BedStatus.AVAILABLE, null)).id();
    }

    private String currentState(UUID encounterId) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM encounter WHERE id = :id", new MapSqlParameterSource("id", encounterId), String.class);
    }

    private UUID insertFixtureEncounter(String state) {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = insertFixtureDepartment();

        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', :state, 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId)
                        .addValue("state", state),
                UUID.class);
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
