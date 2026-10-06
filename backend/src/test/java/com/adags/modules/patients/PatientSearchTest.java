package com.adags.modules.patients;

import com.adags.modules.patients.domain.PatientRepository;
import com.adags.modules.patients.domain.PatientSearchResult;
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

// Regression for the receptionist "find a patient" search: a full-name query
// ("Derick Gabriel Mhidze") must find the patient even though no single name
// column holds the whole string. The search tokenises on whitespace and
// requires every token to match some field, so full names, word reordering,
// and single-word queries all resolve.
class PatientSearchTest extends IntegrationTestSupport {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // Suite-wide singleton Postgres container — nothing rolls back between
    // tests, so a patient inserted here would otherwise leak into others.
    @AfterEach
    void cleanUpPatients() {
        jdbcTemplate.update("TRUNCATE patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void fullNameAcrossGivenMiddleAndFamilyMatches() {
        UUID id = insertPatient("Derick", "Gabriel", "Mhidze");

        List<PatientSearchResult> results = patientRepository.search("Derick Gabriel Mhidze", 20);

        assertThat(results).extracting(PatientSearchResult::id).containsExactly(id);
    }

    @Test
    void givenPlusFamilyWithoutMiddleMatches() {
        UUID id = insertPatient("Derick", "Gabriel", "Mhidze");

        List<PatientSearchResult> results = patientRepository.search("Derick Mhidze", 20);

        assertThat(results).extracting(PatientSearchResult::id).containsExactly(id);
    }

    @Test
    void wordOrderDoesNotMatter() {
        UUID id = insertPatient("Derick", "Gabriel", "Mhidze");

        List<PatientSearchResult> results = patientRepository.search("mhidze derick", 20);

        assertThat(results).extracting(PatientSearchResult::id).containsExactly(id);
    }

    @Test
    void singleTokenStillMatches() {
        UUID id = insertPatient("Derick", "Gabriel", "Mhidze");

        assertThat(patientRepository.search("mhidze", 20)).extracting(PatientSearchResult::id).containsExactly(id);
        assertThat(patientRepository.search("derick", 20)).extracting(PatientSearchResult::id).containsExactly(id);
    }

    @Test
    void everyTokenMustMatch() {
        insertPatient("Derick", "Gabriel", "Mhidze");

        // "Mhidze" matches, "Someone" does not — AND semantics means no result.
        assertThat(patientRepository.search("Mhidze Someone", 20)).isEmpty();
    }

    private UUID insertPatient(String givenName, String middleName, String familyName) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, middle_name, family_name,
                                             date_of_birth, gender, phone)
                        VALUES (:hospitalNo, :givenName, :middleName, :familyName, :dob, 'MALE', :phone)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("hospitalNo", "TEST-" + UUID.randomUUID())
                        .addValue("givenName", givenName)
                        .addValue("middleName", middleName)
                        .addValue("familyName", familyName)
                        .addValue("dob", LocalDate.of(1990, 1, 1))
                        .addValue("phone", "+2557" + (int) (Math.random() * 100000000)),
                UUID.class);
    }
}
