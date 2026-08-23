package com.adags.modules.patients;

import com.adags.modules.patients.domain.PatientRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 05-receptionist.md §3's "Registered today" dashboard tile — a count query.
class PatientRegisteredCountTest extends IntegrationTestSupport {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void countsOnlyPatientsCreatedWithinTheRange() {
        UUID inRange = insertFixturePatient();
        UUID outOfRange = insertFixturePatient();
        jdbcTemplate.update("UPDATE patient SET created_at = :createdAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("createdAt", java.sql.Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)))
                        .addValue("id", outOfRange));

        long count = patientRepository.countCreatedBetween(
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(count).isEqualTo(1);
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }
}
