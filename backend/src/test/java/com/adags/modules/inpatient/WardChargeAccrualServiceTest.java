package com.adags.modules.inpatient;

import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.inpatient.application.WardChargeAccrualService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedDayCalculator;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.BedStatus;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardChargeAccrual;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 04-procedure-bedday-rules.md §3.5. BedDayCalculatorTest already proves
// the boundary math exhaustively against fixed clock-independent
// timestamps; this proves the service's plumbing around it — posting
// through billing, one row per boundary, idempotent reruns, the §3.4
// highest-rate-wins comparison on a same-window transfer, and the "stop
// at the first unbilled gap" rule when no bed overlaps a boundary.
class WardChargeAccrualServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    @Autowired
    private WardChargeAccrualService wardChargeAccrualService;

    @Autowired
    private WardChargeAccrualRepository wardChargeAccrualRepository;

    @Autowired
    private AdmissionRepository admissionRepository;

    @Autowired
    private WardRepository wardRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

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
    void firstRunPostsOneRowPerElapsedBoundaryAndAttachesAnInvoiceLine() {
        UUID wardId = insertFixtureWard(WardClass.GENERAL, 15000);
        UUID bedId = bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.AVAILABLE, null)).id();
        Instant admittedAt = Instant.now(clock).minus(Duration.ofHours(50)); // comfortably over 24h either way
        UUID admissionId = insertFixtureAdmission(admittedAt);
        insertFixtureBedAssignment(admissionId, bedId, admittedAt, null);

        Instant effectiveStart = BedDayCalculator.effectiveStart(admittedAt, FACILITY_ZONE);
        List<Instant> expectedBoundaries = BedDayCalculator.boundariesToPost(effectiveStart, Instant.now(clock), FACILITY_ZONE);

        int posted = wardChargeAccrualService.accrueForAdmission(admissionId);

        assertThat(posted).isEqualTo(expectedBoundaries.size());
        assertThat(posted).isGreaterThanOrEqualTo(1);

        List<WardChargeAccrual> rows = wardChargeAccrualRepository.findByAdmissionId(admissionId);
        assertThat(rows).hasSize(posted);
        for (WardChargeAccrual row : rows) {
            assertThat(row.wardId()).isEqualTo(wardId);
            assertThat(row.dailyRate()).isEqualTo(new Money(15000, "TZS"));
            assertThat(row.invoiceLineId()).isNotNull();
            InvoiceLine line = invoiceLineRepository.findById(row.invoiceLineId()).orElseThrow();
            assertThat(line.grossAmount()).isEqualTo(new Money(15000, "TZS"));
            assertThat(line.sourceEventType()).isEqualTo("WardChargeAccrual");
        }
    }

    @Test
    void rerunningImmediatelyAfterwardsPostsNothingNew() {
        UUID wardId = insertFixtureWard(WardClass.GENERAL, 15000);
        UUID bedId = bedRepository.insert(new Bed(null, wardId, "Bed 1", "STANDARD", BedStatus.AVAILABLE, null)).id();
        Instant admittedAt = Instant.now(clock).minus(Duration.ofHours(50));
        UUID admissionId = insertFixtureAdmission(admittedAt);
        insertFixtureBedAssignment(admissionId, bedId, admittedAt, null);

        int firstRun = wardChargeAccrualService.accrueForAdmission(admissionId);
        int secondRun = wardChargeAccrualService.accrueForAdmission(admissionId);

        assertThat(firstRun).isGreaterThanOrEqualTo(1);
        assertThat(secondRun).isZero();
    }

    @Test
    void aBoundaryWithNoOverlappingBedStopsTheSweepForThatAdmission() {
        Instant admittedAt = Instant.now(clock).minus(Duration.ofHours(50));
        UUID admissionId = insertFixtureAdmission(admittedAt);
        // No BedAssignment ever created — every boundary is unbilled.

        int posted = wardChargeAccrualService.accrueForAdmission(admissionId);

        assertThat(posted).isZero();
        assertThat(wardChargeAccrualRepository.findByAdmissionId(admissionId)).isEmpty();
    }

    // 04-procedure-bedday-rules.md §3.4: "private wins the bed-day" — a
    // transfer within the same boundary window charges the higher rate
    // among every BedAssignment overlapping it, regardless of which one
    // was active for more of the window.
    @Test
    void aTransferWithinTheSameWindowChargesTheHigherRateWard() {
        UUID generalWardId = insertFixtureWard(WardClass.GENERAL, 15000);
        UUID privateWardId = insertFixtureWard(WardClass.PRIVATE, 60000);
        UUID generalBedId = bedRepository.insert(new Bed(null, generalWardId, "G1", "STANDARD", BedStatus.AVAILABLE, null)).id();
        UUID privateBedId = bedRepository.insert(new Bed(null, privateWardId, "P1", "STANDARD", BedStatus.AVAILABLE, null)).id();

        // A single window entirely in the past: admitted 50h ago, moved
        // from general to private 40h ago — both fall inside the first
        // elapsed boundary window.
        Instant admittedAt = Instant.now(clock).minus(Duration.ofHours(50));
        Instant transferredAt = Instant.now(clock).minus(Duration.ofHours(40));
        UUID admissionId = insertFixtureAdmission(admittedAt);
        insertFixtureBedAssignment(admissionId, generalBedId, admittedAt, transferredAt);
        insertFixtureBedAssignment(admissionId, privateBedId, transferredAt, null);

        wardChargeAccrualService.accrueForAdmission(admissionId);

        List<WardChargeAccrual> rows = wardChargeAccrualRepository.findByAdmissionId(admissionId);
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).wardId()).isEqualTo(privateWardId);
        assertThat(rows.get(0).dailyRate()).isEqualTo(new Money(60000, "TZS"));
    }

    private UUID insertFixtureWard(WardClass wardClass, long dailyRateMinor) {
        UUID departmentId = insertFixtureDepartment();
        Ward ward = new Ward(null, BOOTSTRAP_FACILITY_ID, departmentId, "W-" + UUID.randomUUID().toString().substring(0, 6),
                "Test Ward", wardClass, null, new Money(dailyRateMinor, "TZS"), true, null);
        return wardRepository.insert(ward).id();
    }

    private void insertFixtureBedAssignment(UUID admissionId, UUID bedId, Instant assignedAt, Instant releasedAt) {
        jdbcTemplate.update("""
                        INSERT INTO bed_assignment (admission_id, bed_id, assigned_at, released_at, assigned_by)
                        VALUES (:admissionId, :bedId, :assignedAt, :releasedAt,
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource()
                        .addValue("admissionId", admissionId)
                        .addValue("bedId", bedId)
                        .addValue("assignedAt", Timestamp.from(assignedAt))
                        .addValue("releasedAt", releasedAt == null ? null : Timestamp.from(releasedAt)));
    }

    private UUID insertFixtureAdmission(Instant admittedAt) {
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

        return jdbcTemplate.queryForObject("""
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
    }

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }
}
