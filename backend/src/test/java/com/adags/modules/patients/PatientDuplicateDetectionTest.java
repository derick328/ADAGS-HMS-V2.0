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

// 05-receptionist.md §4.1's three tiers. Thresholds are read from the same
// protocol_setting-backed ProtocolSettingReader RegisterPatientService uses
// (V7__protocol_setting.sql) rather than duplicated as local constants, so
// this test can never silently drift from the configured values — see
// backend/CLAUDE.md for the similarity-score evidence behind the defaults.
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

    private double low() {
        return protocolSettings.getDouble("patients.duplicate_detection.low_similarity_threshold", 0.4);
    }

    private double high() {
        return protocolSettings.getDouble("patients.duplicate_detection.high_similarity_threshold", 0.65);
    }

    @Test
    void exactNationalIdMatchBlocksUnconditionally() {
        String nationalId = "NID-" + UUID.randomUUID();
        insertPatient("Amina", "Hassan", LocalDate.of(1990, 1, 1), nationalId, "+255700000001");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Someone", "Else", LocalDate.of(1985, 5, 5), nationalId, "+255700099999", low(), high()));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).blocksUnconditionally()).isTrue();
        assertThat(candidates.get(0).reasons()).contains(MatchReason.NATIONAL_ID_EXACT);
    }

    // Spelling-variant pair (similarity 0.80 — see backend/CLAUDE.md), not an
    // exact-name fixture: the point of this tier is "sounds like the same
    // person, spelled differently," and an exact-name fixture can't tell that
    // apart from the low tier reusing the same 1.0 score with only the DOB
    // changed, which is what the previous version of this test did.
    @Test
    void highNameSimilarityWithMatchingDobBlocksButIsOverridable() {
        insertPatient("Mohamed", "Said", LocalDate.of(1990, 1, 1), null, "+255700000002");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Mohammed", "Said", LocalDate.of(1990, 1, 1), null, "+255799999999", low(), high()));

        assertThat(candidates).isNotEmpty();
        DuplicateCandidate match = candidates.get(0);
        assertThat(match.reasons()).contains(MatchReason.NAME_SIMILARITY_WITH_DOB_MATCH);
        assertThat(match.blocks()).isTrue();
        assertThat(match.blocksUnconditionally()).isFalse();
    }

    // Transposed-letters pair (similarity 0.47 — genuinely between the two
    // thresholds, not an exact name match with a mismatched DOB). DOB is also
    // different here, so a miscalibrated high threshold can't accidentally
    // pull this into NAME_SIMILARITY_WITH_DOB_MATCH and mask a regression.
    @Test
    void lowNameSimilarityWithoutDobMatchOnlyWarns() {
        insertPatient("Neema", "Paul", LocalDate.of(1990, 1, 1), null, "+255700000003");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Neema", "Pual", LocalDate.of(2001, 6, 6), null, "+255799999998", low(), high()));

        assertThat(candidates).isNotEmpty();
        DuplicateCandidate match = candidates.get(0);
        assertThat(match.reasons()).containsExactly(MatchReason.NAME_SIMILARITY);
        assertThat(match.blocks()).isFalse();
    }

    // Sharing only a family name (similarity 0.32) must NOT also produce
    // NAME_SIMILARITY — see backend/CLAUDE.md: a naive low threshold puts
    // "two unrelated people who happen to share a common surname" above the
    // warn line, which is exactly the false-positive rate that trains staff
    // to click through the override without reading it.
    @Test
    void exactPhoneMatchAloneNeverBlocks() {
        // Families share phones — 05-receptionist.md §4.2.
        String phone = "+255711223344";
        insertPatient("Juma", "Mkapa", LocalDate.of(1970, 3, 3), null, phone);

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Zainabu", "Mkapa", LocalDate.of(1995, 8, 8), null, phone, low(), high()));

        assertThat(candidates).isNotEmpty();
        DuplicateCandidate match = candidates.get(0);
        assertThat(match.reasons()).containsExactly(MatchReason.PHONE_EXACT);
        assertThat(match.blocks()).isFalse();
    }

    @Test
    void unrelatedPatientsProduceNoCandidates() {
        insertPatient("Completely", "Unrelated", LocalDate.of(1960, 1, 1), null, "+255700000099");

        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(
                new DuplicateCheckQuery("Totally", "Different", LocalDate.of(1999, 12, 12), null, "+255799912345", low(), high()));

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
