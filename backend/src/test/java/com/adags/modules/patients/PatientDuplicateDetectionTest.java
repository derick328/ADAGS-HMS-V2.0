package com.adags.modules.patients;

import com.adags.modules.patients.domain.DuplicateCandidate;
import com.adags.modules.patients.domain.DuplicateCheckQuery;
import com.adags.modules.patients.domain.MatchReason;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 05-receptionist.md §4.1's three tiers.
class PatientDuplicateDetectionTest extends IntegrationTestSupport {

    private static final double LOW = 0.3;
    private static final double HIGH = 0.6;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void exactNationalIdMatchBlocksUnconditionally() {
        String nationalId = "NID-" + UUID.randomUUID();
        insertPatient("Amina", "Hassan", LocalDate.of(1990, 1, 1), nationalId, "+255700000001");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Someone", "Else", LocalDate.of(1985, 5, 5), nationalId, "+255700099999", LOW, HIGH));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).blocksUnconditionally()).isTrue();
        assertThat(candidates.get(0).reasons()).contains(MatchReason.NATIONAL_ID_EXACT);
    }

    @Test
    void highNameSimilarityWithMatchingDobBlocksButIsOverridable() {
        insertPatient("Amina", "Hassan", LocalDate.of(1990, 1, 1), null, "+255700000002");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Amina", "Hassan", LocalDate.of(1990, 1, 1), null, "+255799999999", LOW, HIGH));

        assertThat(candidates).isNotEmpty();
        DuplicateCandidate match = candidates.get(0);
        assertThat(match.reasons()).contains(MatchReason.NAME_SIMILARITY_WITH_DOB_MATCH);
        assertThat(match.blocks()).isTrue();
        assertThat(match.blocksUnconditionally()).isFalse();
    }

    @Test
    void lowNameSimilarityWithoutDobMatchOnlyWarns() {
        insertPatient("Amina", "Hassan", LocalDate.of(1990, 1, 1), null, "+255700000003");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Amina", "Hassan", LocalDate.of(2001, 6, 6), null, "+255799999998", LOW, HIGH));

        assertThat(candidates).isNotEmpty();
        DuplicateCandidate match = candidates.get(0);
        assertThat(match.reasons()).containsExactly(MatchReason.NAME_SIMILARITY);
        assertThat(match.blocks()).isFalse();
    }

    @Test
    void exactPhoneMatchAloneNeverBlocks() {
        // Families share phones — 05-receptionist.md §4.2.
        String phone = "+255711223344";
        insertPatient("Juma", "Mkapa", LocalDate.of(1970, 3, 3), null, phone);

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Zainabu", "Mkapa", LocalDate.of(1995, 8, 8), null, phone, LOW, HIGH));

        assertThat(candidates).isNotEmpty();
        DuplicateCandidate match = candidates.get(0);
        assertThat(match.reasons()).containsExactly(MatchReason.PHONE_EXACT);
        assertThat(match.blocks()).isFalse();
    }

    @Test
    void unrelatedPatientsProduceNoCandidates() {
        insertPatient("Completely", "Unrelated", LocalDate.of(1960, 1, 1), null, "+255700000099");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Totally", "Different", LocalDate.of(1999, 12, 12), null, "+255799912345", LOW, HIGH));

        assertThat(candidates).isEmpty();
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
