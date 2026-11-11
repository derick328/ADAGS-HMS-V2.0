package com.adags.modules.inpatient;

import com.adags.modules.inpatient.application.WardChargeAccrualSweepService;
import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.BedStatus;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardChargeAccrualRepository;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// A sweep spans every currently-occupied admission — one admission's price
// misconfiguration must not block another's accrual (WardChargeAccrualSweepService's
// per-admission try/catch, not the whole sweep in one transaction).
class WardChargeAccrualSweepServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WardChargeAccrualSweepService sweepService;

    @Autowired
    private WardChargeAccrualRepository wardChargeAccrualRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE ward_charge_accrual, invoice_line, invoice, bed_assignment, admission,
                                 bed, ward, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void sweepPostsForEveryOccupiedAdmissionInOneRun() {
        UUID wardId = insertFixtureWard();
        UUID bedOneId = bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.AVAILABLE, null)).id();
        UUID bedTwoId = bedRepository.insert(new Bed(null, wardId, "Bed 2", "STANDARD", BedStatus.AVAILABLE, null)).id();

        Instant admittedAt = Instant.now(clock).minus(Duration.ofHours(50));
        UUID admissionOne = insertFixtureAdmission(admittedAt, bedOneId);
        UUID admissionTwo = insertFixtureAdmission(admittedAt, bedTwoId);

        int totalPosted = sweepService.sweep();

        assertThat(totalPosted).isGreaterThanOrEqualTo(2);
        assertThat(wardChargeAccrualRepository.findByAdmissionId(admissionOne)).isNotEmpty();
        assertThat(wardChargeAccrualRepository.findByAdmissionId(admissionTwo)).isNotEmpty();
    }

    private UUID insertFixtureWard() {
        UUID departmentId = insertFixtureDepartment();
        Ward ward = new Ward(null, BOOTSTRAP_FACILITY_ID, departmentId, "W-" + UUID.randomUUID().toString().substring(0, 6),
                "Test Ward", WardClass.GENERAL, null, new Money(15000, "TZS"), true, null);
        return wardRepository.insert(ward).id();
    }

    private UUID insertFixtureAdmission(Instant admittedAt, UUID bedId) {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = insertFixtureDepartment();

        UUID encounterId = jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'IN_WARD', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);

        UUID admissionId = jdbcTemplate.queryForObject("""
                        INSERT INTO admission (encounter_id, patient_id, facility_id, admitting_doctor_id,
                                                ordered_ward_class, admitted_at)
                        VALUES (:encounterId, :patientId, :facilityId, (SELECT id FROM app_user WHERE username = 'admin'),
                                'GENERAL', :admittedAt)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("admittedAt", Timestamp.from(admittedAt)),
                UUID.class);

        jdbcTemplate.update("""
                        INSERT INTO bed_assignment (admission_id, bed_id, assigned_at, assigned_by)
                        VALUES (:admissionId, :bedId, :assignedAt, (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("admissionId", admissionId)
                        .addValue("bedId", bedId)
                        .addValue("assignedAt", Timestamp.from(admittedAt)));

        return admissionId;
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
}
