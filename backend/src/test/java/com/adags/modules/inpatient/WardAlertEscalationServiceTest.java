package com.adags.modules.inpatient;

import com.adags.modules.inpatient.application.AcknowledgeWardAlertService;
import com.adags.modules.inpatient.application.WardAlertEscalationService;
import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
import com.adags.modules.inpatient.domain.WardAlertSourceType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 17-ward-nurse.md §4.3: "unacknowledged after N minutes -> escalate to
// CLINICAL_LEAD," mirroring laboratory's own escalation test for the same
// mechanism, replicated per WardAlertNotificationRepository's own comment.
class WardAlertEscalationServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WardAlertNotificationRepository wardAlertNotificationRepository;

    @Autowired
    private AcknowledgeWardAlertService acknowledgeWardAlertService;

    @Autowired
    private WardAlertEscalationService wardAlertEscalationService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE ward_alert_notification, admission, encounter, patient CASCADE",
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM role_assignment WHERE role_id IN "
                        + "(SELECT id FROM role WHERE code = 'CLINICAL_LEAD')",
                new MapSqlParameterSource());
    }

    @Test
    void anOverdueUnacknowledgedAlertEscalatesToTheClinicalLead() {
        UUID admissionId = insertFixtureAdmission();
        UUID notifiedDoctor = bootstrapAdminId();
        WardAlertNotification notification = wardAlertNotificationRepository.insert(WardAlertNotification.raise(
                admissionId, WardAlertSourceType.VITAL_BREACH, UUID.randomUUID(), "SpO2 88%", notifiedDoctor));
        UUID clinicalLeadId = insertFixtureClinicalLead();
        backdateNotification(notification.id(), Instant.now().minusSeconds(30 * 60));

        int escalated = wardAlertEscalationService.escalateOverdue();

        assertThat(escalated).isEqualTo(1);
        WardAlertNotification reloaded = wardAlertNotificationRepository.findByAdmissionId(admissionId).get(0);
        assertThat(reloaded.isEscalated()).isTrue();
        assertThat(reloaded.escalatedToUserId()).isEqualTo(clinicalLeadId);
    }

    @Test
    void anAlertWithinTheWindowDoesNotEscalate() {
        UUID admissionId = insertFixtureAdmission();
        wardAlertNotificationRepository.insert(WardAlertNotification.raise(
                admissionId, WardAlertSourceType.VITAL_BREACH, UUID.randomUUID(), "SpO2 88%", bootstrapAdminId()));
        insertFixtureClinicalLead();
        // notified_at defaults to now() — well inside the 15-minute default window.

        int escalated = wardAlertEscalationService.escalateOverdue();

        assertThat(escalated).isZero();
    }

    @Test
    void anAcknowledgedAlertNeverEscalatesEvenIfOverdue() {
        UUID admissionId = insertFixtureAdmission();
        WardAlertNotification notification = wardAlertNotificationRepository.insert(WardAlertNotification.raise(
                admissionId, WardAlertSourceType.VITAL_BREACH, UUID.randomUUID(), "SpO2 88%", bootstrapAdminId()));
        insertFixtureClinicalLead();
        acknowledgeWardAlertService.acknowledge(notification.id(), bootstrapAdminId());
        backdateNotification(notification.id(), Instant.now().minusSeconds(30 * 60));

        int escalated = wardAlertEscalationService.escalateOverdue();

        assertThat(escalated).isZero();
    }

    // No CLINICAL_LEAD configured anywhere: the sweep must not mark it
    // escalated-to-nobody — that would look like it worked when it didn't.
    @Test
    void anOverdueAlertWithNoClinicalLeadConfiguredStaysUnescalated() {
        UUID admissionId = insertFixtureAdmission();
        WardAlertNotification notification = wardAlertNotificationRepository.insert(WardAlertNotification.raise(
                admissionId, WardAlertSourceType.VITAL_BREACH, UUID.randomUUID(), "SpO2 88%", bootstrapAdminId()));
        backdateNotification(notification.id(), Instant.now().minusSeconds(30 * 60));

        int escalated = wardAlertEscalationService.escalateOverdue();

        assertThat(escalated).isZero();
        WardAlertNotification reloaded = wardAlertNotificationRepository.findByAdmissionId(admissionId).get(0);
        assertThat(reloaded.isEscalated()).isFalse();
    }

    private void backdateNotification(UUID notificationId, Instant notifiedAt) {
        jdbcTemplate.update("UPDATE ward_alert_notification SET notified_at = :notifiedAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("notifiedAt", Timestamp.from(notifiedAt))
                        .addValue("id", notificationId));
    }

    private UUID insertFixtureClinicalLead() {
        UUID userId = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash)
                        VALUES (:username, 'x')
                        RETURNING id
                        """,
                new MapSqlParameterSource("username", "test-clinical-lead-" + UUID.randomUUID()), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'FACILITY', :facilityId FROM role WHERE code = 'CLINICAL_LEAD'
                        """,
                new MapSqlParameterSource().addValue("userId", userId).addValue("facilityId", BOOTSTRAP_FACILITY_ID));
        return userId;
    }

    private UUID insertFixtureAdmission() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);

        UUID departmentId = insertFixtureDepartment();

        UUID encounterId = jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'ADMITTED', 'CASH',
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
                                'GENERAL', now())
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("encounterId", encounterId)
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID),
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
