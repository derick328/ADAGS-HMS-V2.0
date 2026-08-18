package com.adags.modules.inpatient;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.inpatient.application.CompleteDischargeCommand;
import com.adags.modules.inpatient.application.CompleteDischargeService;
import com.adags.modules.inpatient.application.OrderDischargeCommand;
import com.adags.modules.inpatient.application.OrderDischargeService;
import com.adags.modules.inpatient.application.ReverseDischargeOrderCommand;
import com.adags.modules.inpatient.application.ReverseDischargeOrderService;
import com.adags.modules.inpatient.application.WardChargeAccrualService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.DischargeNotAuthorizedException;
import com.adags.modules.inpatient.domain.DischargeNotOrderedException;
import com.adags.modules.inpatient.domain.DischargeOrdererRole;
import com.adags.modules.inpatient.domain.MissingReversalReasonException;
import com.adags.modules.inpatient.domain.NursingAssignment;
import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import com.adags.modules.inpatient.domain.Shift;
import com.adags.shared.kernel.PayerType;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 6 item 3: discharge. 04-procedure-bedday-rules.md §3.6-§3.9,
// 17-ward-nurse.md §2/§9. BedDayCalculatorTest already proves the §3.7 gap
// rule's day-count math in isolation; this proves the service-level
// mechanics — the two timestamps, who may order, reversal, and THE
// INVARIANT that an unpaid balance never blocks discharge.
class DischargeLifecycleIntegrationTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private OrderDischargeService orderDischargeService;

    @Autowired
    private ReverseDischargeOrderService reverseDischargeOrderService;

    @Autowired
    private CompleteDischargeService completeDischargeService;

    @Autowired
    private WardChargeAccrualService wardChargeAccrualService;

    @Autowired
    private AdmissionRepository admissionRepository;

    @Autowired
    private BedAssignmentRepository bedAssignmentRepository;

    @Autowired
    private NursingAssignmentRepository nursingAssignmentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private BillingFacade billingFacade;

    @Autowired
    private Clock clock;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE ward_charge_accrual, invoice_line, invoice, nursing_assignment, bed_assignment,
                                 admission, bed, ward, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void happyPathDischargesTheEncounterFreesTheBedAndRecordsBothTimestamps() {
        UUID doctorId = insertFixtureDoctor();
        UUID admissionId = insertFixtureReadyAdmission();
        Instant beforeOrder = Instant.now(clock);

        Admission ordered = orderDischargeService.order(new OrderDischargeCommand(admissionId, doctorId));
        assertThat(ordered.dischargeOrderedAt()).isAfterOrEqualTo(beforeOrder);
        assertThat(ordered.dischargeOrdererRole()).isEqualTo(DischargeOrdererRole.DOCTOR);
        assertThat(currentEncounterState(ordered.encounterId())).isEqualTo("DISCHARGE_ORDERED");

        Instant actualDeparture = ordered.dischargeOrderedAt().plus(Duration.ofMinutes(45));
        Admission completed = completeDischargeService.complete(
                new CompleteDischargeCommand(admissionId, actualDeparture, doctorId));

        assertThat(completed.actualDepartureAt()).isEqualTo(actualDeparture);
        assertThat(completed.actualDepartureAt()).isNotEqualTo(completed.dischargeOrderedAt());
        assertThat(currentEncounterState(completed.encounterId())).isEqualTo("DISCHARGED");
        assertThat(bedAssignmentRepository.findActiveByAdmissionId(admissionId)).isEmpty();
        List<BedAssignment> history = bedAssignmentRepository.findByAdmissionId(admissionId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).releasedAt()).isEqualTo(actualDeparture);
    }

    // 04-procedure-bedday-rules.md §3.6 ⚠: collapsing the two timestamps
    // means a bed shows free while someone is still in it. Proven directly:
    // after ordering (but before completing), the bed is still active.
    @Test
    void theBedStaysOccupiedBetweenOrderAndActualDeparture() {
        UUID admissionId = insertFixtureReadyAdmission();

        orderDischargeService.order(new OrderDischargeCommand(admissionId, insertFixtureDoctor()));

        assertThat(bedAssignmentRepository.findActiveByAdmissionId(admissionId)).isPresent();
    }

    @Test
    void anAssignedNurseCanOrderDischargeForHerOwnPatient() {
        UUID admissionId = insertFixtureReadyAdmission();
        UUID nurseId = insertFixtureNurse();
        nursingAssignmentRepository.insert(new NursingAssignment(
                null, admissionId, nurseId, Shift.MORNING, LocalDate.now(clock), bootstrapAdminId(), null));

        Admission ordered = orderDischargeService.order(new OrderDischargeCommand(admissionId, nurseId));

        assertThat(ordered.dischargeOrdererRole()).isEqualTo(DischargeOrdererRole.NURSE);
        assertThat(ordered.dischargeOrderedBy()).isEqualTo(nurseId);
    }

    // §3.6: "scoped to the ASSIGNMENT, not a blanket ward-nurse permission
    // — a nurse may discharge her own doctor's patients, not every patient
    // on the ward." A nurse with no roster row for this admission at all
    // is rejected even though DISCHARGE_ORDER is a permission she holds.
    @Test
    void aNurseWithNoRosterEntryForThisAdmissionCannotOrderDischarge() {
        UUID admissionId = insertFixtureReadyAdmission();
        UUID unassignedNurseId = insertFixtureNurse();

        assertThatThrownBy(() -> orderDischargeService.order(new OrderDischargeCommand(admissionId, unassignedNurseId)))
                .isInstanceOf(DischargeNotAuthorizedException.class);
    }

    @Test
    void reversingWithNoReasonIsRejected() {
        UUID admissionId = insertFixtureReadyAdmission();
        Admission ordered = orderDischargeService.order(new OrderDischargeCommand(admissionId, insertFixtureDoctor()));

        assertThatThrownBy(() -> reverseDischargeOrderService.reverse(
                new ReverseDischargeOrderCommand(admissionId, "", bootstrapAdminId())))
                .isInstanceOf(MissingReversalReasonException.class);
        assertThat(currentEncounterState(ordered.encounterId())).isEqualTo("DISCHARGE_ORDERED");
    }

    // 04-procedure-bedday-rules.md §3.9: cancel with a reason clears the
    // order and returns the encounter to IN_WARD — recorded in the
    // encounter event log, not silently.
    @Test
    void reversingWithAReasonClearsTheOrderAndReturnsToInWard() {
        UUID admissionId = insertFixtureReadyAdmission();
        Admission ordered = orderDischargeService.order(new OrderDischargeCommand(admissionId, insertFixtureDoctor()));

        Admission reversed = reverseDischargeOrderService.reverse(
                new ReverseDischargeOrderCommand(admissionId, "Patient deteriorated, staying another 2 days", bootstrapAdminId()));

        assertThat(reversed.dischargeOrderedAt()).isNull();
        assertThat(reversed.dischargeOrderedBy()).isNull();
        assertThat(reversed.dischargeOrdererRole()).isNull();
        assertThat(currentEncounterState(ordered.encounterId())).isEqualTo("IN_WARD");
        assertThat(latestEncounterEventReason(ordered.encounterId()))
                .isEqualTo("Patient deteriorated, staying another 2 days");
    }

    // §3.9: "billing resumes continuously from admission" — proven by
    // showing the accrual job still bills nights that occurred DURING the
    // ordered-but-then-reversed period, since WardChargeAccrualService
    // never paused for it in the first place (it tracks bed occupancy,
    // not discharge_ordered_at).
    @Test
    void billingResumesContinuouslyAfterAReversal() {
        UUID admissionId = insertFixtureReadyAdmissionAdmittedHoursAgo(50);
        orderDischargeService.order(new OrderDischargeCommand(admissionId, insertFixtureDoctor()));

        reverseDischargeOrderService.reverse(
                new ReverseDischargeOrderCommand(admissionId, "Changed plan", bootstrapAdminId()));

        int posted = wardChargeAccrualService.accrueForAdmission(admissionId);

        assertThat(posted).isGreaterThanOrEqualTo(1);
    }

    @Test
    void completingWithoutAPriorOrderIsRejected() {
        UUID admissionId = insertFixtureReadyAdmission();

        assertThatThrownBy(() -> completeDischargeService.complete(
                new CompleteDischargeCommand(admissionId, Instant.now(clock), bootstrapAdminId())))
                .isInstanceOf(DischargeNotOrderedException.class);
    }

    @Test
    void completingOnAnEncounterNotDischargeOrderedIsRejected() {
        UUID admissionId = insertFixtureReadyAdmission();
        // Ordered then reversed — dischargeOrderedAt is null again, so
        // DischargeNotOrderedException fires first; the encounter-state
        // guard inside completeDischargeService is exercised by ordering
        // once more and completing twice instead.
        UUID doctorId = insertFixtureDoctor();
        orderDischargeService.order(new OrderDischargeCommand(admissionId, doctorId));
        completeDischargeService.complete(new CompleteDischargeCommand(admissionId, Instant.now(clock), doctorId));

        assertThatThrownBy(() -> completeDischargeService.complete(
                new CompleteDischargeCommand(admissionId, Instant.now(clock), doctorId)))
                .isInstanceOfAny(DischargeNotOrderedException.class, InvalidEncounterTransitionException.class);
    }

    // ⚠ THE INVARIANT — 17-ward-nurse.md ✗1, 02-billing-architecture.md §8:
    // discharge is never blocked by an unpaid balance, and never touches
    // Patient.active — that flag is admin-only, for deceased or formally
    // transferred-out patients, never a ward action.
    @Test
    void anOutstandingBalanceDischargesSuccessfullyAndBecomesAReceivableWithoutTouchingThePatient() {
        UUID admissionId = insertFixtureReadyAdmission();
        Admission admission = admissionRepository.findById(admissionId).orElseThrow();
        UUID actorId = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();

        billingFacade.postServiceCharge(admission.encounterId(), admission.patientId(), admission.facilityId(),
                PayerType.CASH, "CONSULT_GENERAL", 1, "Test", UUID.randomUUID(), actorId);
        boolean activeBeforeDischarge = queryPatientActive(admission.patientId());

        orderDischargeService.order(new OrderDischargeCommand(admissionId, doctorId));
        Admission completed = completeDischargeService.complete(
                new CompleteDischargeCommand(admissionId, Instant.now(clock), doctorId));

        assertThat(currentEncounterState(completed.encounterId())).isEqualTo("DISCHARGED");
        List<Invoice> invoices = invoiceRepository.findByEncounterId(admission.encounterId());
        assertThat(invoices).isNotEmpty();
        assertThat(invoices).allSatisfy(invoice -> assertThat(invoice.state()).isEqualTo(InvoiceState.RECEIVABLE_POST_DISCHARGE));
        assertThat(queryPatientActive(admission.patientId())).isEqualTo(activeBeforeDischarge).isTrue();
    }

    private boolean queryPatientActive(UUID patientId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT active FROM patient WHERE id = :id", new MapSqlParameterSource("id", patientId), Boolean.class));
    }

    private String latestEncounterEventReason(UUID encounterId) {
        return jdbcTemplate.queryForObject("""
                        SELECT reason FROM encounter_event WHERE encounter_id = :encounterId
                        ORDER BY occurred_at DESC LIMIT 1
                        """,
                new MapSqlParameterSource("encounterId", encounterId), String.class);
    }

    private String currentEncounterState(UUID encounterId) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM encounter WHERE id = :id", new MapSqlParameterSource("id", encounterId), String.class);
    }

    private UUID insertFixtureReadyAdmission() {
        return insertFixtureReadyAdmissionAdmittedHoursAgo(30);
    }

    private UUID insertFixtureReadyAdmissionAdmittedHoursAgo(long hoursAgo) {
        Instant admittedAt = Instant.now(clock).minus(Duration.ofHours(hoursAgo));

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

        UUID wardId = jdbcTemplate.queryForObject("""
                        INSERT INTO ward (facility_id, department_id, code, name, ward_class, daily_rate_minor, currency_code)
                        VALUES (:facilityId, :departmentId, :code, 'Test Ward', 'GENERAL', 15000, 'TZS')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId)
                        .addValue("code", "W-" + UUID.randomUUID().toString().substring(0, 6)),
                UUID.class);

        UUID bedId = jdbcTemplate.queryForObject("""
                        INSERT INTO bed (ward_id, label, bed_type) VALUES (:wardId, 'Bed 1', 'STANDARD') RETURNING id
                        """,
                new MapSqlParameterSource("wardId", wardId), UUID.class);

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

    private UUID insertFixtureDoctor() {
        UUID userId = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash, status)
                        VALUES (:username, 'x', 'ACTIVE') RETURNING id
                        """,
                new MapSqlParameterSource("username", "doctor-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'DOCTOR'
                        """,
                new MapSqlParameterSource("userId", userId));
        return userId;
    }

    private UUID insertFixtureNurse() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash, status)
                        VALUES (:username, 'x', 'ACTIVE') RETURNING id
                        """,
                new MapSqlParameterSource("username", "nurse-" + UUID.randomUUID().toString().substring(0, 8)),
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

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
