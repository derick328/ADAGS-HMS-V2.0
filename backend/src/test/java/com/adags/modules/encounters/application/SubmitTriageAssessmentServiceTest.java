package com.adags.modules.encounters.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.EncounterType;
import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.modules.encounters.domain.TriageAssessmentRepository;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.PatientDemographics;
import com.adags.shared.kernel.PayerType;
import com.adags.shared.settings.ProtocolSettingReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 06-triage.md §6 + 02-billing-architecture.md §4.1/§5. Pure Mockito, no
// Spring context and no Postgres — this is deliberately NOT the
// Testcontainers-backed proof against a real BillingFacadeImpl (that's
// SubmitTriageAssessmentIntegrationTest and EndToEndBillingTest). It
// proves the nine-step ordering — including the transition, event, and
// outbox publish — in isolation from real pricing/role resolution, with
// a mock BillingFacade standing in for the now-real one.
@ExtendWith(MockitoExtension.class)
class SubmitTriageAssessmentServiceTest {

    private static final UUID ENCOUNTER_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID PROVIDER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private TriageAssessmentRepository triageAssessmentRepository;
    @Mock
    private ClinicalFacade clinicalFacade;
    @Mock
    private BillingFacade billingFacade;
    @Mock
    private PatientFacade patientFacade;
    @Mock
    private ProtocolSettingReader protocolSettingReader;
    @Mock
    private DomainEventPublisher eventPublisher;

    private SubmitTriageAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new SubmitTriageAssessmentService(
                encounterRepository, triageAssessmentRepository, clinicalFacade, billingFacade,
                patientFacade, protocolSettingReader, eventPublisher, CLOCK);
    }

    @Test
    void missingReferralIsRejectedBeforeAnyWrite() {
        SubmitTriageAssessmentCommand command = immediateCommand(null);

        assertThatThrownBy(() -> service.submit(command)).isInstanceOf(MissingReferralException.class);

        verifyNoInteractions(encounterRepository, triageAssessmentRepository, clinicalFacade,
                billingFacade, patientFacade, protocolSettingReader, eventPublisher);
    }

    @Test
    void anEncounterNotInAwaitingTriageIsRejectedBeforeAnyWrite() {
        when(encounterRepository.findById(ENCOUNTER_ID)).thenReturn(
                Optional.of(encounter(EncounterState.AWAITING_CONSULT, 3)));

        assertThatThrownBy(() -> service.submit(immediateCommand(PROVIDER_ID)))
                .isInstanceOf(InvalidEncounterTransitionException.class);

        verifyNoInteractions(triageAssessmentRepository, clinicalFacade, billingFacade,
                patientFacade, protocolSettingReader, eventPublisher);
    }

    // A provider holding neither DOCTOR nor SPECIALIST_DOCTOR (or any
    // other pricing failure) still rolls everything back — proves steps
    // 1-5 are attempted in the right order and steps 7-9 are never reached.
    @Test
    void immediatePathAttemptsBypassAndAssignmentBeforeTheChargeFailsAndNeverTransitions() {
        stubHappyPathCollaborators();
        RuntimeException billingFailure = new IllegalStateException("holds neither DOCTOR nor SPECIALIST_DOCTOR");
        doThrow(billingFailure).when(billingFacade).postConsultationCharge(
                eq(ENCOUNTER_ID), eq(PATIENT_ID), any(), any(), any(), eq(PROVIDER_ID), eq(ACTOR_ID));

        assertThatThrownBy(() -> service.submit(immediateCommand(PROVIDER_ID))).isSameAs(billingFailure);

        InOrder order = inOrder(clinicalFacade, triageAssessmentRepository, encounterRepository, billingFacade);
        order.verify(clinicalFacade).recordTriageVitalSigns(
                eq(ENCOUNTER_ID), eq(PATIENT_ID), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(), eq(ACTOR_ID), any());
        order.verify(triageAssessmentRepository).insert(any());
        order.verify(clinicalFacade).appendAllergyRecord(eq(PATIENT_ID), eq(ENCOUNTER_ID), anyString(), eq(ACTOR_ID), eq("TRIAGE"));
        order.verify(encounterRepository).assignProviderAndType(ENCOUNTER_ID, PROVIDER_ID, EncounterType.EMERGENCY, 3);
        order.verify(billingFacade).createImmediateTriagePaymentBypass(eq(ENCOUNTER_ID), anyString(), eq(ACTOR_ID));
        order.verify(billingFacade).postConsultationCharge(
                eq(ENCOUNTER_ID), eq(PATIENT_ID), any(), any(), any(), eq(PROVIDER_ID), eq(ACTOR_ID));

        verify(encounterRepository, never()).updateState(any(), any());
        verify(encounterRepository, never()).insertEvent(any());
        verifyNoInteractions(eventPublisher);
    }

    // The "cleaner way to prove it" the item-3 brief invited: substitute a
    // BillingFacade that succeeds and verify the FULL nine-step order,
    // including the transition/event/outbox steps that are unreachable
    // against the real (always-throwing) facade today.
    @Test
    void immediatePathCompletesAllNineStepsInOrderWhenBillingSucceeds() {
        stubHappyPathCollaborators();
        // billingFacade.postConsultationCharge is a void method — the
        // default Mockito stub already "succeeds" (does nothing), so no
        // explicit stubbing is needed to simulate Phase-3 billing working.

        TriageAssessment result = service.submit(immediateCommand(PROVIDER_ID));

        assertThat(result.priority()).isEqualTo(TriagePriority.IMMEDIATE);

        InOrder order = inOrder(clinicalFacade, triageAssessmentRepository, encounterRepository,
                billingFacade, eventPublisher);
        order.verify(clinicalFacade).recordTriageVitalSigns(
                eq(ENCOUNTER_ID), eq(PATIENT_ID), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(), eq(ACTOR_ID), any());
        order.verify(triageAssessmentRepository).insert(any());
        order.verify(clinicalFacade).appendAllergyRecord(eq(PATIENT_ID), eq(ENCOUNTER_ID), anyString(), eq(ACTOR_ID), eq("TRIAGE"));
        order.verify(encounterRepository).assignProviderAndType(ENCOUNTER_ID, PROVIDER_ID, EncounterType.EMERGENCY, 3);
        order.verify(billingFacade).createImmediateTriagePaymentBypass(eq(ENCOUNTER_ID), anyString(), eq(ACTOR_ID));
        order.verify(billingFacade).postConsultationCharge(
                eq(ENCOUNTER_ID), eq(PATIENT_ID), any(), any(), any(), eq(PROVIDER_ID), eq(ACTOR_ID));
        order.verify(encounterRepository).updateState(ENCOUNTER_ID, EncounterState.AWAITING_CONSULT);
        order.verify(encounterRepository).insertEvent(any());
        order.verify(eventPublisher).publish(eq("TriageCompleted"), any());
    }

    @Test
    void nonImmediatePathTargetsAwaitingPaymentAndNeverCreatesABypass() {
        stubHappyPathCollaborators();

        SubmitTriageAssessmentCommand command = nonImmediateCommand(PROVIDER_ID, TriagePriority.URGENT);
        TriageAssessment result = service.submit(command);

        assertThat(result.priority()).isEqualTo(TriagePriority.URGENT);
        verify(billingFacade, never()).createImmediateTriagePaymentBypass(any(), any(), any());
        verify(encounterRepository).assignProviderAndType(ENCOUNTER_ID, PROVIDER_ID, EncounterType.OUTPATIENT, 3);
        verify(encounterRepository).updateState(ENCOUNTER_ID, EncounterState.AWAITING_PAYMENT);
    }

    private void stubHappyPathCollaborators() {
        when(encounterRepository.findById(ENCOUNTER_ID)).thenReturn(
                Optional.of(encounter(EncounterState.AWAITING_TRIAGE, 3)));
        when(patientFacade.findDemographics(PATIENT_ID)).thenReturn(
                Optional.of(new PatientDemographics(LocalDate.of(1990, 1, 1), "MALE")));
        when(protocolSettingReader.getDouble("triage.pregnancy_prompt.min_age_years")).thenReturn(12.0);
        when(protocolSettingReader.getDouble("triage.pregnancy_prompt.max_age_years")).thenReturn(50.0);
        when(clinicalFacade.recordTriageVitalSigns(
                any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID());
        when(triageAssessmentRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clinicalFacade.appendAllergyRecord(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        when(encounterRepository.assignProviderAndType(any(), any(), any(), anyInt())).thenReturn(4);
        // Not every test's priority reaches this call (URGENT never does) — lenient so those don't fail on it.
        lenient().when(billingFacade.createImmediateTriagePaymentBypass(any(), any(), any())).thenReturn(UUID.randomUUID());
    }

    private Encounter encounter(EncounterState state, int version) {
        return new Encounter(ENCOUNTER_ID, PATIENT_ID, UUID.randomUUID(), UUID.randomUUID(), null, null,
                EncounterType.OUTPATIENT, state, PayerType.CASH, version, Instant.now(CLOCK), ACTOR_ID);
    }

    private SubmitTriageAssessmentCommand immediateCommand(UUID providerId) {
        return new SubmitTriageAssessmentCommand(
                ENCOUNTER_ID, 3, providerId,
                "Severe bleeding", "Post-partum haemorrhage", "NKDA", "None", ModeOfAmbulation.STRETCHER_ASSISTED,
                false, null, null,
                false, null,
                false,
                null, null,
                TriagePriority.IMMEDIATE, ImmediateReason.ACTIVE_UNCONTROLLED_BLEEDING, null,
                null, null,
                37.0, 90, 55, 130, 24, 90, 60, 165, "PAIN", 5.0,
                ACTOR_ID, Instant.now(CLOCK));
    }

    private SubmitTriageAssessmentCommand nonImmediateCommand(UUID providerId, TriagePriority priority) {
        return new SubmitTriageAssessmentCommand(
                ENCOUNTER_ID, 3, providerId,
                "Fever and headache", "Since yesterday", "NKDA", "None", ModeOfAmbulation.WALKING,
                false, null, null,
                false, null,
                false,
                null, null,
                priority, null, null,
                null, null,
                36.8, 120, 80, 75, 16, 98, 70, 175, "ALERT", null,
                ACTOR_ID, Instant.now(CLOCK));
    }
}
