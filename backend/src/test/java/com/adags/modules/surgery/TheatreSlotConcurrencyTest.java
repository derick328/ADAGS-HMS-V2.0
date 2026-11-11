package com.adags.modules.surgery;

import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryUrgency;
import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import com.adags.modules.surgery.domain.TheatreSlotNotAvailableException;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// root CLAUDE.md "Concurrency": UNIQUE (theatre_id, scheduled_date,
// time_slot) WHERE status != 'CANCELLED' — "That index is what prevents
// two procedures in one theatre slot — a read-then-write check loses the
// race." Fires real concurrent transactions at the same slot and proves
// the database rejects all but one, independent of any application-level
// pre-check.
class TheatreSlotConcurrencyTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TheatreRepository theatreRepository;

    @Autowired
    private SurgeryOrderRepository surgeryOrderRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE surgery_order, theatre, encounter, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void concurrentSchedulingIntoTheSameSlotLeavesExactlyOneBooking() throws Exception {
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID invoiceLineId = UUID.randomUUID();
        UUID scheduledBy = bootstrapAdminId();
        LocalDate slotDate = LocalDate.now().plusDays(1);

        int racers = 5;
        List<UUID> encounterIds = IntStream.range(0, racers).mapToObj(i -> insertFixtureEncounter()).toList();

        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<SurgeryOrder>> futures = encounterIds.stream()
                    .map(encounterId -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            SurgeryOrder toInsert = SurgeryOrder.schedule(
                                    encounterId, "APPENDECTOMY", "Appendectomy", "GENERAL_SURGERY",
                                    SurgeryUrgency.ELECTIVE, "GENERAL", slotDate, "MORNING", theatreId, 60,
                                    List.of(), null, true, "s3://consents/abc.pdf", invoiceLineId, scheduledBy);
                            return surgeryOrderRepository.insert(toInsert);
                        } catch (TheatreSlotNotAvailableException ex) {
                            return null;
                        }
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long successes = futures.stream().map(this::resolve).filter(r -> r != null).count();
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        Integer bookedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surgery_order WHERE theatre_id = :theatreId AND status != 'CANCELLED'",
                new MapSqlParameterSource("theatreId", theatreId), Integer.class);
        assertThat(bookedCount).isEqualTo(1);
    }

    @Test
    void cancellingAFreesTheSlotForAnother() {
        UUID theatreId = theatreRepository.insert(new Theatre(null, BOOTSTRAP_FACILITY_ID, "Theatre 1", true, null)).id();
        UUID invoiceLineId = UUID.randomUUID();
        UUID scheduledBy = bootstrapAdminId();
        LocalDate slotDate = LocalDate.now().plusDays(1);
        UUID firstEncounter = insertFixtureEncounter();

        SurgeryOrder first = surgeryOrderRepository.insert(SurgeryOrder.schedule(
                firstEncounter, "APPENDECTOMY", "Appendectomy", "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE,
                "GENERAL", slotDate, "MORNING", theatreId, 60, List.of(), null, true, "s3://consents/abc.pdf",
                invoiceLineId, scheduledBy));

        jdbcTemplate.update("UPDATE surgery_order SET status = 'CANCELLED' WHERE id = :id",
                new MapSqlParameterSource("id", first.id()));

        UUID secondEncounter = insertFixtureEncounter();
        SurgeryOrder second = surgeryOrderRepository.insert(SurgeryOrder.schedule(
                secondEncounter, "APPENDECTOMY", "Appendectomy", "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE,
                "GENERAL", slotDate, "MORNING", theatreId, 60, List.of(), null, true, "s3://consents/abc.pdf",
                invoiceLineId, scheduledBy));

        assertThat(second.id()).isNotNull();
    }

    private Object resolve(Future<SurgeryOrder> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID insertFixtureEncounter() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);

        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'IN_CONSULT', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
