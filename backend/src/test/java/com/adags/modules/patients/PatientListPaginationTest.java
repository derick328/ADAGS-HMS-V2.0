package com.adags.modules.patients;

import com.adags.modules.patients.domain.PatientListResult;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The doctor workspace's "All patients" tab and the admin Patients page page
// through the registration list 15 at a time. Two things have to hold or the
// pager lies: consecutive pages must not repeat or skip a row, and the total
// must be counted over the same predicate as the rows (merged records
// excluded from both).
class PatientListPaginationTest extends IntegrationTestSupport {

    private static final int PAGE_SIZE = 15;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // Suite-wide singleton Postgres container — nothing rolls back between
    // tests, so patients inserted here would otherwise leak into others.
    @AfterEach
    void cleanUpPatients() {
        jdbcTemplate.update("TRUNCATE patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void consecutivePagesCoverEveryRowExactlyOnce() {
        List<UUID> newestFirst = insertPatients(17);

        List<PatientListResult> firstPage = patientRepository.listRecent(null, null, null, 0, PAGE_SIZE);
        List<PatientListResult> secondPage = patientRepository.listRecent(null, null, null, PAGE_SIZE, PAGE_SIZE);

        assertThat(firstPage).hasSize(PAGE_SIZE);
        assertThat(secondPage).hasSize(2);
        assertThat(firstPage).extracting(PatientListResult::id)
                .containsExactlyElementsOf(newestFirst.subList(0, PAGE_SIZE));
        assertThat(secondPage).extracting(PatientListResult::id)
                .containsExactlyElementsOf(newestFirst.subList(PAGE_SIZE, 17));
        assertThat(secondPage).extracting(PatientListResult::id)
                .doesNotContainAnyElementsOf(firstPage.stream().map(PatientListResult::id).toList());
    }

    @Test
    void pastTheLastPageReturnsNoRowsRatherThanWrappingAround() {
        insertPatients(17);

        assertThat(patientRepository.listRecent(null, null, null, 2 * PAGE_SIZE, PAGE_SIZE)).isEmpty();
    }

    @Test
    void totalCountMatchesTheRowsThePagesActuallyReturn() {
        insertPatients(17);

        long total = patientRepository.countRecent(null, null, null);

        assertThat(total).isEqualTo(17);
        int paged = 0;
        for (int page = 0; page * PAGE_SIZE < total; page++) {
            paged += patientRepository.listRecent(null, null, null, page * PAGE_SIZE, PAGE_SIZE).size();
        }
        assertThat(paged).isEqualTo(17);
    }

    @Test
    void mergedPatientsLeaveTheListAndTheCountTogether() {
        List<UUID> newestFirst = insertPatients(3);
        UUID merged = newestFirst.get(0);
        UUID survivor = newestFirst.get(2);
        jdbcTemplate.update("UPDATE patient SET merged_into_id = :survivor WHERE id = :merged",
                new MapSqlParameterSource().addValue("survivor", survivor).addValue("merged", merged));

        assertThat(patientRepository.countRecent(null, null, null)).isEqualTo(2);
        assertThat(patientRepository.listRecent(null, null, null, 0, PAGE_SIZE))
                .extracting(PatientListResult::id)
                .doesNotContain(merged)
                .hasSize(2);
    }

    /** Returns the ids in the order the list orders them — newest registration first. */
    private List<UUID> insertPatients(int count) {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<UUID> newestFirst = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            newestFirst.add(insertPatient("Patient" + i, base.minus(i, ChronoUnit.MINUTES)));
        }
        return newestFirst;
    }

    private UUID insertPatient(String givenName, Instant createdAt) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name,
                                             date_of_birth, gender, phone, created_at)
                        VALUES (:hospitalNo, :givenName, 'Mhidze', :dob, 'MALE', :phone, :createdAt)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("hospitalNo", "TEST-" + UUID.randomUUID())
                        .addValue("givenName", givenName)
                        .addValue("dob", LocalDate.of(1990, 1, 1))
                        .addValue("phone", "+2557" + (int) (Math.random() * 100000000))
                        .addValue("createdAt", Timestamp.from(createdAt)),
                UUID.class);
    }
}
