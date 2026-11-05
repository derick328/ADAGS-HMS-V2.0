package com.adags.modules.patients;

import com.adags.modules.patients.domain.DuplicateCandidate;
import com.adags.modules.patients.domain.DuplicateCheckQuery;
import com.adags.modules.patients.domain.MatchReason;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.shared.settings.ProtocolSettingReader;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Registration matches an exact national ID or a similar full name.
class PatientDuplicateDetectionTest extends IntegrationTestSupport {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProtocolSettingReader protocolSettings;

    @Autowired
    private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;

    // IntegrationTestSupport's Postgres container is a suite-wide singleton
    // (see its class comment) — nothing rolls back between tests, so a
    // patient inserted here would otherwise still be visible to the next
    // test's duplicate check.
    @AfterEach
    void cleanUpPatients() {
        jdbcTemplate.update("TRUNCATE patient CASCADE",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource());
    }

    private double high() {
        return protocolSettings.getDouble("patients.duplicate_detection.high_similarity_threshold", 0.65);
    }

    @Test
    void exactNationalIdMatchBlocksUnconditionally() {
        String nationalId = "NID-" + UUID.randomUUID();
        insertPatient("Amina", "Hassan", LocalDate.of(1990, 1, 1), nationalId, "+255700000001");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Someone", null, "Else", nationalId, high()));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).blocks()).isTrue();
        assertThat(candidates.get(0).reasons()).contains(MatchReason.NATIONAL_ID_EXACT);
    }

    // Spelling variant (similarity 0.80) blocks even without a matching DOB.
    @Test
    void similarFullNameBlocksWithoutDobMatch() {
        insertPatient("Mohamed", "Said", LocalDate.of(1990, 1, 1), null, "+255700000002");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Mohammed", null, "Said", null, high()));

        assertThat(candidates).isNotEmpty();
        DuplicateCandidate match = candidates.get(0);
        assertThat(match.reasons()).contains(MatchReason.NAME_SIMILARITY);
        assertThat(match.blocks()).isTrue();
    }

    // A weak name match is not enough to claim the patient already exists.
    @Test
    void lowNameSimilarityDoesNotFlagARegisteredPatient() {
        insertPatient("Neema", "Paul", LocalDate.of(1990, 1, 1), null, "+255700000003");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Neema", null, "Pual", null, high()));

        assertThat(candidates).isEmpty();
    }

    @Test
    void exactPhoneMatchAloneIsNotACandidate() {
        // Families share phones — 05-receptionist.md §4.2.
        String phone = "+255711223344";
        insertPatient("Juma", "Mkapa", LocalDate.of(1970, 3, 3), null, phone);

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Zainabu", null, "Mkapa", null, high()));

        assertThat(candidates).isEmpty();
    }

    @Test
    void unrelatedPatientsProduceNoCandidates() {
        insertPatient("Completely", "Unrelated", LocalDate.of(1960, 1, 1), null, "+255700000099");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Totally", null, "Different", null, high()));

        assertThat(candidates).isEmpty();
    }

    @Test
    void middleNameIsPartOfFullNameMatchAndCandidateDisplay() {
        UUID patientId = insertPatient("Amina", "Hassan", LocalDate.of(1990, 1, 1), null, "+255700000099");
        jdbcTemplate.update("UPDATE patient SET middle_name = 'Salma  Rose' WHERE id = :id",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", patientId));

        List<DuplicateCandidate> sameFullName = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("AMINA", "  Salma Rose  ", "HASSAN", null, high()));
        assertThat(sameFullName).hasSize(1);
        assertThat(sameFullName.get(0).reasons()).contains(MatchReason.FULL_NAME_EXACT);
        assertThat(sameFullName.get(0).fullName()).isEqualTo("Amina Salma  Rose Hassan");

        List<DuplicateCandidate> differentMiddleName = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Amina", "Unrelated", "Hassan", null, high()));
        assertThat(differentMiddleName).isEmpty();
    }

    @Test
    void findByIdFollowsTheMergePointer() {
        UUID survivorId = insertPatient("Survivor", "Record", LocalDate.of(1980, 1, 1), null, "+255700000010");
        UUID originalId = insertPatient("Original", "Record", LocalDate.of(1980, 1, 1), null, "+255700000011");

        jdbcTemplate.update("UPDATE patient SET merged_into_id = :survivor WHERE id = :original",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("survivor", survivorId).addValue("original", originalId));

        Patient resolved = patientRepository.findById(originalId).orElseThrow();
        assertThat(resolved.id()).isEqualTo(survivorId);
    }

    private UUID insertPatient(String givenName, String familyName, LocalDate dob, String nationalId, String phone) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone, national_id)
                        VALUES (:hospitalNo, :givenName, :familyName, :dob, 'FEMALE', :phone, :nationalId)
                        RETURNING id
                        """,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("hospitalNo", "TEST-" + UUID.randomUUID())
                        .addValue("givenName", givenName)
                        .addValue("familyName", familyName)
                        .addValue("dob", dob)
                        .addValue("phone", phone)
                        .addValue("nationalId", nationalId, java.sql.Types.VARCHAR),
                UUID.class);
    }
}
