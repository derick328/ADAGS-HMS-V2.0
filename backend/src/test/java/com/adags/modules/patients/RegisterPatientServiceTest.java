package com.adags.modules.patients;

import com.adags.modules.patients.application.DuplicatePatientException;
import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 05-receptionist.md §4.3: the whole registration transaction.
class RegisterPatientServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RegisterPatientService registerPatientService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // IntegrationTestSupport's Postgres container is a suite-wide singleton
    // (see its class comment) — nothing rolls back between tests, so a
    // patient registered by one test (e.g. "Amina Hassan" here and in
    // PatientDuplicateDetectionTest) would otherwise still be visible to a
    // later test's duplicate check and produce a false block.
    @AfterEach
    void cleanUpPatients() {
        jdbcTemplate.update("TRUNCATE patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void registersAPatientWithHospitalNumberNextOfKinAuditAndEvent() {
        UUID actor = bootstrapAdminId();
        RegisterPatientCommand command = commandFor("Amina", "Hassan", actor);

        Patient saved = registerPatientService.register(command);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.hospitalNo()).matches(Pattern.quote("MAIN-") + "\\d{4}-\\d{6}");
        assertThat(saved.active()).isTrue();
        assertThat(saved.isMerged()).isFalse();

        Integer nextOfKinCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM next_of_kin WHERE patient_id = :id",
                new MapSqlParameterSource("id", saved.id()), Integer.class);
        assertThat(nextOfKinCount).isEqualTo(1);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'PATIENT_REGISTERED' AND resource_id = :id",
                new MapSqlParameterSource("id", saved.id()), Integer.class);
        assertThat(auditCount).isEqualTo(1);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'PatientRegistered' AND payload->>'patientId' = :id",
                new MapSqlParameterSource("id", saved.id().toString()), Integer.class);
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    void derivesDateOfBirthFromAgeAndFlagsItEstimated() {
        RegisterPatientCommand base = commandFor("NoDob", "Patient", bootstrapAdminId());
        RegisterPatientCommand withAgeOnly = new RegisterPatientCommand(
                base.givenName(), base.middleName(), base.familyName(), null, 34, 2,
                base.gender(), base.maritalStatus(), base.nationalId(), base.phone(), base.email(),
                base.occupation(), base.nextOfKin(), base.addresses(), base.insurancePolicy(),
                base.facilityId(), base.actorUserId(), null);

        Patient saved = registerPatientService.register(withAgeOnly);

        assertThat(saved.dobIsEstimated()).isTrue();
        assertThat(saved.dateOfBirth()).isBefore(LocalDate.now().minusYears(33));
    }

    @Test
    void exactNationalIdDuplicateBlocksRegistrationEvenWithOverrideReason() {
        UUID actor = bootstrapAdminId();
        String nationalId = "NID-" + UUID.randomUUID();
        registerPatientService.register(withNationalId(commandFor("First", "Registration", actor), nationalId));

        RegisterPatientCommand duplicate = withOverride(
                withNationalId(commandFor("Second", "Registration", actor), nationalId),
                "confirmed not the same person");

        assertThatThrownBy(() -> registerPatientService.register(duplicate))
                .isInstanceOf(DuplicatePatientException.class);
    }

    @Test
    void highSimilarityWithDobMatchBlocksUnlessOverridden() {
        UUID actor = bootstrapAdminId();
        RegisterPatientCommand first = commandFor("Amina", "Hassan", actor);
        registerPatientService.register(first);

        RegisterPatientCommand secondNoOverride = new RegisterPatientCommand(
                "Amina", null, "Hassan", first.dateOfBirth(), null, null,
                "FEMALE", null, null, "+255799999911", null, null,
                first.nextOfKin(), first.addresses(), null, BOOTSTRAP_FACILITY_ID, actor, null);

        assertThatThrownBy(() -> registerPatientService.register(secondNoOverride))
                .isInstanceOf(DuplicatePatientException.class);

        RegisterPatientCommand secondWithOverride = withOverride(secondNoOverride, "confirmed not the same person");
        Patient saved = registerPatientService.register(secondWithOverride);
        assertThat(saved.id()).isNotNull();
    }

    private RegisterPatientCommand commandFor(String givenName, String familyName, UUID actor) {
        return new RegisterPatientCommand(
                givenName, null, familyName, LocalDate.of(1990, 4, 12), null, null,
                "FEMALE", "SINGLE", null, "+255700" + (100000 + Math.abs(givenName.hashCode() % 900000)), null, null,
                new NextOfKin(null, null, "Next Kin", "SIBLING", null, "+255711000000", false),
                List.of(), null, BOOTSTRAP_FACILITY_ID, actor, null);
    }

    private RegisterPatientCommand withNationalId(RegisterPatientCommand command, String nationalId) {
        return new RegisterPatientCommand(
                command.givenName(), command.middleName(), command.familyName(), command.dateOfBirth(),
                command.ageYears(), command.ageMonths(), command.gender(), command.maritalStatus(), nationalId,
                command.phone(), command.email(), command.occupation(), command.nextOfKin(), command.addresses(),
                command.insurancePolicy(), command.facilityId(), command.actorUserId(), command.notSamePersonConfirmationReason());
    }

    private RegisterPatientCommand withOverride(RegisterPatientCommand command, String reason) {
        return new RegisterPatientCommand(
                command.givenName(), command.middleName(), command.familyName(), command.dateOfBirth(),
                command.ageYears(), command.ageMonths(), command.gender(), command.maritalStatus(), command.nationalId(),
                command.phone(), command.email(), command.occupation(), command.nextOfKin(), command.addresses(),
                command.insurancePolicy(), command.facilityId(), command.actorUserId(), reason);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
