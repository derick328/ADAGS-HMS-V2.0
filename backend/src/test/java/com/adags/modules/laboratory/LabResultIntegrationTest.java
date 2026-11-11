package com.adags.modules.laboratory;

import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.application.SubmitTriageAssessmentCommand;
import com.adags.modules.encounters.application.SubmitTriageAssessmentService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.modules.laboratory.api.LabResultDraftController;
import com.adags.modules.laboratory.api.LabReportController;
import org.springframework.security.access.AccessDeniedException;
import com.adags.modules.laboratory.api.RecordLabResultRequest;
import com.adags.modules.laboratory.api.ParameterValueRequest;
import com.adags.shared.authz.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.server.ResponseStatusException;
import com.adags.modules.laboratory.application.AcknowledgeCriticalResultService;
import com.adags.modules.laboratory.application.AmendLabResultCommand;
import com.adags.modules.laboratory.application.AmendLabResultService;
import com.adags.modules.laboratory.application.CollectSpecimenCommand;
import com.adags.modules.laboratory.application.CollectSpecimenService;
import com.adags.modules.laboratory.application.CriticalResultEscalationService;
import com.adags.modules.laboratory.application.ParameterEntry;
import com.adags.modules.laboratory.application.PlaceLabOrderCommand;
import com.adags.modules.laboratory.application.PlaceLabOrderService;
import com.adags.modules.laboratory.application.ReagentEntry;
import com.adags.modules.laboratory.application.RecordLabResultCommand;
import com.adags.modules.laboratory.application.RecordLabResultResult;
import com.adags.modules.laboratory.application.RecordLabResultService;
import com.adags.modules.laboratory.application.VerifyLabResultCommand;
import com.adags.modules.laboratory.application.VerifyLabResultService;
import com.adags.modules.laboratory.domain.CriticalNotificationAlreadyAcknowledgedException;
import com.adags.modules.laboratory.domain.CriticalResultNotification;
import com.adags.modules.laboratory.domain.CriticalResultNotificationRepository;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.MissingAmendmentReasonException;
import com.adags.modules.laboratory.domain.MissingMachineOutputException;
import com.adags.modules.laboratory.domain.MissingOverrideReasonException;
import com.adags.modules.laboratory.domain.ResultFlag;
import com.adags.modules.laboratory.domain.ResultNotPendingVerificationException;
import com.adags.modules.laboratory.domain.Sex;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenNotProcessableException;
import com.adags.modules.laboratory.domain.SpecimenQuality;
import com.adags.modules.laboratory.domain.SpecimenStatus;
import com.adags.modules.laboratory.domain.Urgency;
import com.adags.modules.laboratory.domain.VerificationLevel;
import com.adags.modules.laboratory.domain.VerificationState;
import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 4 items 4-5: result entry, release/verification, critical alerting
// escalation. EndToEndBillingTest carries the cross-module "order -> gate
// -> critical result releases unpaid" backbone proof; this file covers the
// laboratory-module-internal behaviour in depth (override validation,
// machine output mandate, amendment, verification, escalation timing).
class LabResultIntegrationTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RegisterPatientService registerPatientService;
    @Autowired
    private OpenEncounterService openEncounterService;
    @Autowired
    private SubmitTriageAssessmentService submitTriageAssessmentService;
    @Autowired
    private PlaceLabOrderService placeLabOrderService;
    @Autowired
    private CollectSpecimenService collectSpecimenService;
    @Autowired
    private RecordLabResultService recordLabResultService;
    @Autowired
    private AmendLabResultService amendLabResultService;
    @Autowired
    private VerifyLabResultService verifyLabResultService;
    @Autowired
    private AcknowledgeCriticalResultService acknowledgeCriticalResultService;
    @Autowired
    private CriticalResultEscalationService criticalResultEscalationService;
    @Autowired
    private LabTestRepository labTestRepository;
    @Autowired
    private LabTestParameterRepository labTestParameterRepository;
    @Autowired
    private LabReferenceRangeRepository labReferenceRangeRepository;
    @Autowired
    private LabResultRepository labResultRepository;
    @Autowired
    private com.adags.modules.laboratory.application.LabResultViewStateService viewState;
    @Autowired
    private CriticalResultNotificationRepository criticalResultNotificationRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private LabResultDraftController draftController;
    @Autowired
    private LabReportController labReportController;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE critical_result_notification, lab_result_reagent, lab_result_parameter_value,
                                 lab_result, specimen, lab_order, invoice_line, invoice, payment_bypass,
                                 encounter_event, triage_assessment, vital_reading, vital_signs, allergy_record,
                                 outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM role_assignment WHERE role_id IN "
                        + "(SELECT id FROM role WHERE code IN ('DOCTOR', 'CLINICAL_LEAD'))",
                new MapSqlParameterSource());
    }

    @Test
    void generatedFormValuesComputeFlagsAndSnapshotTheAppliedRange() {
        Fixture fixture = readySpecimen("LAB-CHEM-" + UUID.randomUUID());

        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), "ISE", "lot 24-0817", "Findings", "Interpretation",
                "Ref range note", "Conclusion", "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)),
                List.of(new ReagentEntry(null, 2.0, "lot 24-0817"))));

        assertThat(recorded.result().enteredAt()).isNotNull();
        assertThat(recorded.result().releasedAt()).isNotNull();
        assertThat(recorded.parameterValues()).hasSize(1);
        assertThat(recorded.parameterValues().get(0).flag()).isEqualTo(ResultFlag.NORMAL);
        assertThat(recorded.parameterValues().get(0).appliedRangeId()).isEqualTo(fixture.rangeId());
    }

    // §5.2: "Override permitted with a reason" — the technician disagreeing
    // with the computed flag must say why.
    @Test
    void overridingTheComputedFlagWithoutAReasonIsRejected() {
        Fixture fixture = readySpecimen("LAB-CHEM-" + UUID.randomUUID());

        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, ResultFlag.HIGH, null)), List.of())))
                .isInstanceOf(MissingOverrideReasonException.class);
    }

    @Test
    void overridingTheComputedFlagWithAReasonIsAccepted() {
        Fixture fixture = readySpecimen("LAB-CHEM-" + UUID.randomUUID());

        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, ResultFlag.HIGH, "haemolysed sample, clinically HIGH")),
                List.of()));

        assertThat(recorded.parameterValues().get(0).flag()).isEqualTo(ResultFlag.HIGH);
        assertThat(recorded.parameterValues().get(0).flagOverridden()).isTrue();
    }

    // 16-lab-verification-model.md v0.2 §4: mandatory whenever the test's
    // catalogue entry says produces_machine_output=true.
    @Test
    void aTestRequiringMachineOutputRejectsEntryWithoutIt() {
        Fixture fixture = readySpecimen("LAB-CHEM-" + UUID.randomUUID());

        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, null,
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of())))
                .isInstanceOf(MissingMachineOutputException.class);
    }

    @Test
    void assessedSampleTypeOverrideAcceptsOptionalReasonAndRecordsItWhenPresent() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        Patient patient = registerPatientService.register(patientCommand("Asha", "Specimen-" + UUID.randomUUID(), admin));
        Encounter checkedIn = checkInAndTriageImmediate(patient, doctorId);
        String code = "LAB-TYPE-" + UUID.randomUUID();
        insertFixtureLabTestWithParameter(code, 4_000, admin);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                checkedIn.id(), code, null, Urgency.ROUTINE, doctorId));

        Specimen withoutReason = collectSpecimenService.collect(new CollectSpecimenCommand(
                order.id(), SpecimenQuality.ADEQUATE, null, "Whole Blood (EDTA)", null, doctorId));
        assertThat(withoutReason.sampleTypeOverrideReason()).isNull();

        Specimen accepted = collectSpecimenService.collect(new CollectSpecimenCommand(
                order.id(), SpecimenQuality.ADEQUATE, null, "Whole Blood (EDTA)",
                "Catalogue type is generic; verified EDTA tube at receipt", doctorId));
        assertThat(accepted.sampleType()).isEqualTo("Whole Blood (EDTA)");
        assertThat(accepted.sampleTypeOverrideReason()).contains("EDTA tube");
    }

    @Test
    void releasedResultReadStateIsPerUserAndIdempotent() {
        Fixture fixture = readySpecimen("LAB-READ-" + UUID.randomUUID());
        LabResult result = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)),
                List.of())).result();
        assertThat(viewState.unreadOrderIds(fixture.actorId())).contains(result.labOrderId());
        viewState.markViewed(fixture.actorId(), List.of(result));
        viewState.markViewed(fixture.actorId(), List.of(result));
        assertThat(viewState.unreadOrderIds(fixture.actorId())).doesNotContain(result.labOrderId());
        Integer views = jdbcTemplate.queryForObject("SELECT count(*) FROM lab_result_view_state WHERE result_id = :id",
                new MapSqlParameterSource("id", result.id()), Integer.class);
        assertThat(views).isEqualTo(1);
        UUID anotherUser = insertFixtureDoctor();
        viewState.markViewed(anotherUser, List.of(result));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lab_result_view_state WHERE result_id = :id",
                new MapSqlParameterSource("id", result.id()), Integer.class)).isEqualTo(2);
    }

    @Test
    void aResultCannotBeEnteredAgainstARejectedSpecimen() {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        Patient patient = registerPatientService.register(patientCommand("Ally", "Rashid", admin));
        Encounter checkedIn = checkInAndTriageImmediate(patient, doctorId);
        LabCatalogueFixture catalogue = insertFixtureLabTestWithParameter("LAB-CHEM-" + UUID.randomUUID(), 4_000, admin);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                checkedIn.id(), labTestCode(catalogue.labTestId()), null, Urgency.ROUTINE, doctorId));
        Specimen rejected = collectSpecimenService.collect(new CollectSpecimenCommand(
                order.id(), SpecimenQuality.CLOTTED, "clotted on receipt", doctorId));

        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                rejected.id(), doctorId, null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(catalogue.parameterId(), 4.2, null, null)), List.of())))
                .isInstanceOf(SpecimenNotProcessableException.class);
    }

    // v0.1 §5 (unchanged by v0.2): new row, original readable, reason required.
    @Test
    void amendingCreatesANewRowAndMarksTheOriginalAmendedWhileLeavingItReadable() {
        Fixture fixture = readySpecimen("LAB-CHEM-" + UUID.randomUUID());
        RecordLabResultResult original = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of()));

        RecordLabResultResult amended = amendLabResultService.amend(new AmendLabResultCommand(
                original.result().id(), fixture.actorId(), "transcription error, re-checked", null, null, null,
                null, null, null, "s3://bucket/key-2",
                List.of(new ParameterEntry(fixture.parameterId(), 5.9, null, null)), List.of()));

        assertThat(amended.result().amendsId()).isEqualTo(original.result().id());
        assertThat(amended.result().amendmentReason()).isEqualTo("transcription error, re-checked");
        assertThat(amended.parameterValues().get(0).resultValue()).isEqualTo(5.9);

        LabResult reloadedOriginal = labResultRepository.findById(original.result().id()).orElseThrow();
        assertThat(reloadedOriginal.verificationState()).isEqualTo(VerificationState.AMENDED);
        // Original row's own parameter values are untouched — still readable as they were entered.
        assertThat(labResultRepository.findBySpecimenId(fixture.specimenId())).hasSize(2);
    }

    @Test
    void amendingWithoutAReasonIsRejected() {
        Fixture fixture = readySpecimen("LAB-CHEM-" + UUID.randomUUID());
        RecordLabResultResult original = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of()));

        assertThatThrownBy(() -> amendLabResultService.amend(new AmendLabResultCommand(
                original.result().id(), fixture.actorId(), "  ", null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.5, null, null)), List.of())))
                .isInstanceOf(MissingAmendmentReasonException.class);
    }

    // 16-lab-verification-model.md v0.2 §8: dormant today (LAB_RESULT_VERIFY
    // granted to nobody) but the mechanism itself must already work.
    @Test
    void verifyingAPendingResultTransitionsItToVerified() {
        Fixture fixture = readySpecimenWithVerificationLevel(
                "LAB-CHEM-" + UUID.randomUUID(), VerificationLevel.SECOND_PERSON);
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of()));
        assertThat(recorded.result().verificationState()).isEqualTo(VerificationState.PENDING);

        UUID verifierId = insertFixtureDoctor();
        verifyLabResultService.verify(new VerifyLabResultCommand(recorded.result().id(), verifierId, "confirmed, matches machine output"));

        LabResult verified = labResultRepository.findById(recorded.result().id()).orElseThrow();
        assertThat(verified.verificationState()).isEqualTo(VerificationState.VERIFIED);
        assertThat(verified.verifiedBy()).isEqualTo(verifierId);
        assertThat(verified.resultVersionHash()).isNotBlank();
    }

    @Test
    void verifyingAResultThatIsNotPendingIsRejected() {
        // NONE-level test: verification_state starts NOT_REQUIRED, never PENDING.
        Fixture fixture = readySpecimen("LAB-CHEM-" + UUID.randomUUID());
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of()));
        assertThat(recorded.result().verificationState()).isEqualTo(VerificationState.NOT_REQUIRED);

        assertThatThrownBy(() -> verifyLabResultService.verify(
                new VerifyLabResultCommand(recorded.result().id(), insertFixtureDoctor(), null)))
                .isInstanceOf(ResultNotPendingVerificationException.class);
    }

    // v0.2 §2's CHECK constraint — the real guard behind "a technician
    // cannot verify their own entry" when the level requires a second person.
    @Test
    void selfVerificationIsRejectedByTheDatabaseWhenTheLevelRequiresASecondPerson() {
        Fixture fixture = readySpecimenWithVerificationLevel(
                "LAB-CHEM-" + UUID.randomUUID(), VerificationLevel.SECOND_PERSON);
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of()));

        assertThatThrownBy(() -> verifyLabResultService.verify(
                new VerifyLabResultCommand(recorded.result().id(), fixture.actorId(), "self-check")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void acknowledgingACriticalNotificationTwiceIsRejected() {
        Fixture fixture = readySpecimen("LAB-CRIT-" + UUID.randomUUID());
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 7.5, null, null)), List.of()));
        UUID notificationId = recorded.criticalNotifications().get(0).id();

        acknowledgeCriticalResultService.acknowledge(notificationId, fixture.actorId());

        assertThatThrownBy(() -> acknowledgeCriticalResultService.acknowledge(notificationId, fixture.actorId()))
                .isInstanceOf(CriticalNotificationAlreadyAcknowledgedException.class);
    }

    // §7: "unacknowledged after N minutes -> escalate to CLINICAL_LEAD."
    @Test
    void anOverdueUnacknowledgedCriticalNotificationEscalatesToTheClinicalLead() {
        Fixture fixture = readySpecimen("LAB-CRIT-" + UUID.randomUUID());
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 7.5, null, null)), List.of()));
        UUID notificationId = recorded.criticalNotifications().get(0).id();
        UUID clinicalLeadId = insertFixtureClinicalLead();
        backdateNotification(notificationId, Instant.now().minusSeconds(30 * 60));

        int escalated = criticalResultEscalationService.escalateOverdue();

        assertThat(escalated).isEqualTo(1);
        CriticalResultNotification reloaded = criticalResultNotificationRepository.findById(notificationId).orElseThrow();
        assertThat(reloaded.isEscalated()).isTrue();
        assertThat(reloaded.escalatedToUserId()).isEqualTo(clinicalLeadId);
    }

    @Test
    void aCriticalNotificationWithinTheWindowDoesNotEscalate() {
        Fixture fixture = readySpecimen("LAB-CRIT-" + UUID.randomUUID());
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 7.5, null, null)), List.of()));
        insertFixtureClinicalLead();
        // notified_at defaults to now() — well inside the 15-minute default window.

        int escalated = criticalResultEscalationService.escalateOverdue();

        assertThat(escalated).isZero();
        CriticalResultNotification reloaded = criticalResultNotificationRepository
                .findById(recorded.criticalNotifications().get(0).id()).orElseThrow();
        assertThat(reloaded.isEscalated()).isFalse();
    }

    @Test
    void anAcknowledgedCriticalNotificationNeverEscalatesEvenIfOverdue() {
        Fixture fixture = readySpecimen("LAB-CRIT-" + UUID.randomUUID());
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 7.5, null, null)), List.of()));
        UUID notificationId = recorded.criticalNotifications().get(0).id();
        insertFixtureClinicalLead();
        acknowledgeCriticalResultService.acknowledge(notificationId, fixture.actorId());
        backdateNotification(notificationId, Instant.now().minusSeconds(30 * 60));

        int escalated = criticalResultEscalationService.escalateOverdue();

        assertThat(escalated).isZero();
    }

    // No CLINICAL_LEAD configured anywhere: the sweep must not mark it
    // escalated-to-nobody — that would look like it worked when it didn't.
    @Test
    void anOverdueNotificationWithNoClinicalLeadConfiguredStaysUnescalated() {
        Fixture fixture = readySpecimen("LAB-CRIT-" + UUID.randomUUID());
        RecordLabResultResult recorded = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 7.5, null, null)), List.of()));
        UUID notificationId = recorded.criticalNotifications().get(0).id();
        backdateNotification(notificationId, Instant.now().minusSeconds(30 * 60));

        int escalated = criticalResultEscalationService.escalateOverdue();

        assertThat(escalated).isZero();
        CriticalResultNotification reloaded = criticalResultNotificationRepository.findById(notificationId).orElseThrow();
        assertThat(reloaded.isEscalated()).isFalse();
    }

    @Test
    void numericSingleValueRequiresARealNumberAndUnit() {
        Fixture fixture = readySpecimen("LAB-SINGLE-" + UUID.randomUUID());
        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(), List.of(), "NUMERIC", "not a number", "mmol/L",
                null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(), List.of(), "NUMERIC", "5.2", null,
                null, null, null))).isInstanceOf(IllegalArgumentException.class);
        var released = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(), List.of(), "NUMERIC", "5.2", "mmol/L",
                null, null, null));
        assertThat(released.result().singleValue()).isEqualTo("5.2");
        assertThat(released.result().singleValueUnit()).isEqualTo("mmol/L");
        assertThat(released.parameterValues()).isEmpty();
    }

    @Test
    void qualitativeValueIsStoredWithoutAFalseNumericParameter() {
        Fixture fixture = readySpecimen("LAB-QUAL-" + UUID.randomUUID());
        RecordLabResultResult released = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(), List.of(), "QUALITATIVE", "Positive", null,
                "ABNORMAL", null, null));
        LabResult reloaded = labResultRepository.findById(released.result().id()).orElseThrow();
        assertThat(reloaded.singleValue()).isEqualTo("Positive");
        assertThat(reloaded.resultType()).isEqualTo("QUALITATIVE");
        assertThat(released.parameterValues()).isEmpty();
        assertThat(released.criticalNotifications()).isEmpty();
    }

    @Test
    void aCriticalValueCannotBeDowngradedOrHiddenByOverallNormal() {
        Fixture fixture = readySpecimen("LAB-CRIT-" + UUID.randomUUID());
        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(new ParameterEntry(fixture.parameterId(), 7.5,
                ResultFlag.NORMAL, "disputed")), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(new ParameterEntry(fixture.parameterId(), 7.5,
                null, null)), List.of(), "NUMERIC", null, null, "NORMAL", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(labResultRepository.findBySpecimenId(fixture.specimenId())).isEmpty();
    }

    @Test
    void reportDownloadRequiresResultReadPermission() {
        Fixture fixture = readySpecimen("LAB-REPORT-" + UUID.randomUUID());
        RecordLabResultResult released = recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of()));
        assertThatThrownBy(() -> labReportController.url(released.result().id(), new Actor(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateCatalogueParameterAndSecondInitialReleaseAreRejected() {
        Fixture fixture = readySpecimen("LAB-DUP-" + UUID.randomUUID());
        assertThatThrownBy(() -> recordLabResultService.record(new RecordLabResultCommand(
                fixture.specimenId(), fixture.actorId(), null, null, null, null, null, null,
                "s3://bucket/key", List.of(
                        new ParameterEntry(fixture.parameterId(), 4.2, null, null),
                        new ParameterEntry(fixture.parameterId(), 4.3, null, null)), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        RecordLabResultCommand valid = new RecordLabResultCommand(fixture.specimenId(), fixture.actorId(),
                null, null, null, null, null, null, "s3://bucket/key",
                List.of(new ParameterEntry(fixture.parameterId(), 4.2, null, null)), List.of());
        recordLabResultService.record(valid);
        assertThatThrownBy(() -> recordLabResultService.record(valid)).isInstanceOf(DataAccessException.class);
        assertThat(labResultRepository.findBySpecimenId(fixture.specimenId())).hasSize(1);
    }

    @Test
    void draftCanBeSavedReopenedAndSubmittedOnlyOnce() {
        Fixture fixture = readySpecimen("LAB-DRAFT-" + UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'LAB_TECHNICIAN'
                """, new MapSqlParameterSource("userId", fixture.actorId()));
        UUID orderId = jdbcTemplate.queryForObject("SELECT lab_order_id FROM specimen WHERE id = :id",
                new MapSqlParameterSource("id", fixture.specimenId()), UUID.class);
        Actor actor = new Actor(fixture.actorId());
        var partial = objectMapper.createObjectNode().put("findings", "work in progress");
        var saved = draftController.save(orderId, new LabResultDraftController.SaveDraftRequest(0L, partial), actor);
        assertThat(saved.status()).isEqualTo("DRAFT");
        assertThat(labResultRepository.findByLabOrderId(orderId)).isEmpty();
        assertThat(draftController.get(orderId, actor).payload().get("findings").asText()).isEqualTo("work in progress");
        var edited = draftController.save(orderId,
                new LabResultDraftController.SaveDraftRequest(saved.version(), partial.put("findings", "complete")), actor);
        assertThatThrownBy(() -> draftController.save(orderId,
                new LabResultDraftController.SaveDraftRequest(saved.version(), partial), actor))
                .isInstanceOf(ResponseStatusException.class);
        jdbcTemplate.update("""
                INSERT INTO lab_machine_output_upload (object_key, lab_order_id, uploaded_by)
                VALUES ('s3://bucket/key', :orderId, :userId)
                """, new MapSqlParameterSource().addValue("orderId", orderId).addValue("userId", fixture.actorId()));
        var request = new RecordLabResultRequest(fixture.specimenId(), null, null, "complete", null, null,
                null, "s3://bucket/key", List.of(new ParameterValueRequest(fixture.parameterId(), 4.2, null, null)), List.of());
        var released = draftController.submit(orderId,
                new LabResultDraftController.SubmitDraftRequest(edited.version(), request), actor);
        assertThat(released.releasedAt()).isNotNull();
        assertThat(draftController.get(orderId, actor).status()).isEqualTo("SUBMITTED");
        assertThatThrownBy(() -> draftController.submit(orderId,
                new LabResultDraftController.SubmitDraftRequest(edited.version(), request), actor))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(labResultRepository.findByLabOrderId(orderId)).hasSize(1);
    }

    private record Fixture(UUID specimenId, UUID parameterId, UUID rangeId, UUID actorId) {
    }

    private record LabCatalogueFixture(UUID labTestId, UUID parameterId, UUID rangeId) {
    }

    private Fixture readySpecimen(String testCode) {
        return readySpecimenWithVerificationLevel(testCode, VerificationLevel.NONE);
    }

    private Fixture readySpecimenWithVerificationLevel(String testCode, VerificationLevel verificationLevel) {
        UUID admin = bootstrapAdminId();
        UUID doctorId = insertFixtureDoctor();
        Patient patient = registerPatientService.register(patientCommand("Test", "Patient-" + UUID.randomUUID(), admin));
        Encounter checkedIn = checkInAndTriageImmediate(patient, doctorId);
        LabCatalogueFixture catalogue = insertFixtureLabTest(testCode, 4_000, admin, verificationLevel);
        LabOrder order = placeLabOrderService.placeOrder(new PlaceLabOrderCommand(
                checkedIn.id(), testCode, null, Urgency.ROUTINE, doctorId));
        Specimen specimen = collectSpecimenService.collect(
                new CollectSpecimenCommand(order.id(), SpecimenQuality.ADEQUATE, null, doctorId));
        return new Fixture(specimen.id(), catalogue.parameterId(), catalogue.rangeId(), doctorId);
    }

    private LabCatalogueFixture insertFixtureLabTestWithParameter(String code, long priceMinor, UUID createdBy) {
        return insertFixtureLabTest(code, priceMinor, createdBy, VerificationLevel.NONE);
    }

    private LabCatalogueFixture insertFixtureLabTest(String code, long priceMinor, UUID createdBy, VerificationLevel verificationLevel) {
        jdbcTemplate.update("INSERT INTO service_item (code, name, category) VALUES (:code, :code, 'LAB')",
                new MapSqlParameterSource("code", code));
        UUID serviceItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM service_item WHERE code = :code", new MapSqlParameterSource("code", code), UUID.class);
        UUID priceListId = jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = 'CASH'", new MapSqlParameterSource(), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
                        VALUES (:priceListId, :serviceItemId, :amountMinor, :createdBy)
                        """,
                new MapSqlParameterSource()
                        .addValue("priceListId", priceListId)
                        .addValue("serviceItemId", serviceItemId)
                        .addValue("amountMinor", priceMinor)
                        .addValue("createdBy", createdBy));

        LabTest labTest = labTestRepository.insert(LabTest.create(
                code, code, "BLOOD", "EDTA_TUBE", 24, verificationLevel, true));
        LabTestParameter parameter = labTestParameterRepository.insert(
                LabTestParameter.create(labTest.id(), "K", "Potassium", "mmol/L", 1, "ISE", "2823-3"));
        LabReferenceRange range = labReferenceRangeRepository.insert(LabReferenceRange.create(
                parameter.id(), 216, null, Sex.ANY, 0.0, 15.0, 3.5, 5.0, 2.5, 6.5, createdBy));
        return new LabCatalogueFixture(labTest.id(), parameter.id(), range.id());
    }

    private String labTestCode(UUID labTestId) {
        return labTestRepository.findById(labTestId).orElseThrow().code();
    }

    private Encounter checkInAndTriageImmediate(Patient patient, UUID doctorId) {
        UUID admin = bootstrapAdminId();
        Encounter checkedIn = openEncounterService.checkIn(
                new OpenEncounterCommand(patient.id(), insertFixtureDepartment(), null, admin));
        submitTriageAssessmentService.submit(new SubmitTriageAssessmentCommand(
                checkedIn.id(), checkedIn.version(), doctorId,
                "Fever and headache", "Since yesterday", "NKDA", "None", ModeOfAmbulation.WALKING,
                false, null, null, false, null, false, PregnancyStatus.NO, null,
                TriagePriority.IMMEDIATE, ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING, null,
                null, null,
                36.8, 120, 80, 75, 16, 98, 70, 175, "ALERT", null,
                doctorId, Instant.now()));
        return checkedIn;
    }

    private void backdateNotification(UUID notificationId, Instant notifiedAt) {
        jdbcTemplate.update("UPDATE critical_result_notification SET notified_at = :notifiedAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("notifiedAt", java.sql.Timestamp.from(notifiedAt))
                        .addValue("id", notificationId));
    }

    private RegisterPatientCommand patientCommand(String givenName, String familyName, UUID actor) {
        return new RegisterPatientCommand(
                givenName, null, familyName, LocalDate.of(1990, 4, 12), null, null,
                "FEMALE", "SINGLE", null, "+255700" + (100000 + Math.abs((givenName + familyName).hashCode() % 900000)),
                null, null,
                new NextOfKin(null, null, "Next Kin", "SIBLING", null, "+255711000000", false),
                List.of(), null, BOOTSTRAP_FACILITY_ID, actor, null);
    }

    private UUID insertFixtureDoctor() {
        UUID userId = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (username, password_hash)
                        VALUES (:username, 'x')
                        RETURNING id
                        """,
                new MapSqlParameterSource("username", "test-doctor-" + UUID.randomUUID()), UUID.class);
        jdbcTemplate.update("""
                        INSERT INTO role_assignment (user_id, role_id, scope_type, scope_id)
                        SELECT :userId, id, 'GLOBAL', NULL FROM role WHERE code = 'DOCTOR'
                        """,
                new MapSqlParameterSource("userId", userId));
        return userId;
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

    private UUID insertFixtureDepartment() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name)
                        VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code)
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
