package com.adags.modules.inpatient;

import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.BedStatus;
import com.adags.modules.inpatient.domain.GenderRestriction;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V31__ward_bed_admission.sql. Proves the CHECK constraints the migration
// adds to the pre-existing `ward` table, and that findFreeByWardId excludes
// a bed with an active BedAssignment.
class WardBedRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE bed_assignment, bed, admission, ward, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertAndFindRoundTripsAllFields() {
        UUID departmentId = insertFixtureDepartment();

        Ward toInsert = new Ward(null, BOOTSTRAP_FACILITY_ID, departmentId, "GEN-A", "General Ward A",
                WardClass.GENERAL, GenderRestriction.FEMALE, new Money(15000, "TZS"), true, null);
        Ward saved = wardRepository.insert(toInsert);

        assertThat(saved.id()).isNotNull();
        Ward reloaded = wardRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.name()).isEqualTo("General Ward A");
        assertThat(reloaded.wardClass()).isEqualTo(WardClass.GENERAL);
        assertThat(reloaded.genderRestriction()).isEqualTo(GenderRestriction.FEMALE);
        assertThat(reloaded.dailyRate()).isEqualTo(new Money(15000, "TZS"));
        assertThat(reloaded.active()).isTrue();
    }

    @Test
    void genderRestrictionIsNullableForAnUnrestrictedWard() {
        UUID departmentId = insertFixtureDepartment();

        Ward saved = wardRepository.insert(new Ward(null, BOOTSTRAP_FACILITY_ID, departmentId, "MIX-A", "Mixed Ward A",
                WardClass.GENERAL, null, new Money(10000, "TZS"), true, null));

        assertThat(wardRepository.findById(saved.id()).orElseThrow().genderRestriction()).isNull();
    }

    @Test
    void checkConstraintRejectsANegativeDailyRate() {
        UUID departmentId = insertFixtureDepartment();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ward (facility_id, department_id, code, name, ward_class, daily_rate_minor, currency_code)
                        VALUES (:facilityId, :departmentId, 'BAD-1', 'Bad Ward', 'GENERAL', -1, 'TZS')
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void checkConstraintRejectsAnUnrecognisedWardClass() {
        UUID departmentId = insertFixtureDepartment();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ward (facility_id, department_id, code, name, ward_class, daily_rate_minor, currency_code)
                        VALUES (:facilityId, :departmentId, 'BAD-2', 'Bad Ward', 'ICU', 10000, 'TZS')
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void findFreeByWardIdExcludesABedWithAnActiveAssignment() {
        UUID wardId = insertFixtureWard();
        Bed free = bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.AVAILABLE, null));
        Bed occupied = bedRepository.insert(new Bed(null, wardId, "Bed 2", "STANDARD", BedStatus.AVAILABLE, null));
        insertFixtureAdmissionActiveBed(occupied.id());

        List<Bed> result = bedRepository.findFreeByWardId(wardId);

        assertThat(result).extracting(Bed::id).containsExactly(free.id());
    }

    @Test
    void findFreeByWardIdExcludesAnOutOfServiceBed() {
        UUID wardId = insertFixtureWard();
        bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.OUT_OF_SERVICE, null));

        assertThat(bedRepository.findFreeByWardId(wardId)).isEmpty();
    }

    private UUID insertFixtureWard() {
        UUID departmentId = insertFixtureDepartment();
        return wardRepository.insert(new Ward(null, BOOTSTRAP_FACILITY_ID, departmentId, "W-" + UUID.randomUUID().toString().substring(0, 6),
                "Test Ward", WardClass.GENERAL, null, new Money(10000, "TZS"), true, null)).id();
    }

    private UUID insertFixtureAdmissionActiveBed(UUID bedId) {
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
                                'GENERAL', now())
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID),
                UUID.class);

        jdbcTemplate.update("""
                        INSERT INTO bed_assignment (admission_id, bed_id, assigned_by)
                        VALUES (:admissionId, :bedId, (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("admissionId", admissionId)
                        .addValue("bedId", bedId));

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
