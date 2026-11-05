package com.adags.modules.encounters.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterEvent;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.EncounterType;
import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.PregnancyPromptPolicy;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.modules.encounters.domain.TriageAssessmentRepository;
import com.adags.modules.encounters.domain.TriagePriority;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.PatientDemographics;
import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 06-triage.md §6 + 02-billing-architecture.md §4.1/§5: the ONE transaction
// that submits a triage assessment. Nine steps, in the order both specs
// give, all rolled back together if any step fails — most importantly step
// 6, which always throws today (no billing module exists yet: see
// BillingModuleNotImplementedException) and is the whole point — "a
// triaged patient with no charge walks straight past the cashier" is
// prevented by making that impossible right now, not by trusting every
// future caller to remember the invariant.
//
// Invariants exercised here (root CLAUDE.md):
//  - Billing: consultation charge posts inside this transaction; IMMEDIATE
//    bypasses payment for the whole encounter, never a single charge; the
//    gate itself is untouched by triage.
//  - Concurrency: Encounter.version is checked with a real UPDATE ... WHERE
//    predicate (JdbcEncounterRepository.assignProviderAndType), not a
//    read-then-write check.
//  - Data: AllergyRecord is appended, never overwritten; TriageAssessment
//    itself is a fresh row (supersedes_id links a correction, never an
//    in-place edit).
@Service
public class SubmitTriageAssessmentService {

    private final EncounterRepository encounterRepository;
    private final TriageAssessmentRepository triageAssessmentRepository;
    private final ClinicalFacade clinicalFacade;
    private final BillingFacade billingFacade;
    private final PatientFacade patientFacade;
    private final ProtocolSettingReader protocolSettingReader;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public SubmitTriageAssessmentService(
            EncounterRepository encounterRepository,
            TriageAssessmentRepository triageAssessmentRepository,
            ClinicalFacade clinicalFacade,
            BillingFacade billingFacade,
            PatientFacade patientFacade,
            ProtocolSettingReader protocolSettingReader,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.encounterRepository = encounterRepository;
        this.triageAssessmentRepository = triageAssessmentRepository;
        this.clinicalFacade = clinicalFacade;
        this.billingFacade = billingFacade;
        this.patientFacade = patientFacade;
        this.protocolSettingReader = protocolSettingReader;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public TriageAssessment submit(SubmitTriageAssessmentCommand command) {
        // §5: referral is mandatory — rejected, not defaulted, before any write.
        if (command.assignedProviderId() == null) {
            throw new MissingReferralException();
        }

        Encounter encounter = encounterRepository.findById(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        boolean immediate = command.priority() == TriagePriority.IMMEDIATE;
        EncounterState targetState = immediate ? EncounterState.AWAITING_CONSULT : EncounterState.AWAITING_PAYMENT;
        if (!encounter.state().canTransitionTo(targetState)) {
            throw new InvalidEncounterTransitionException(encounter.state(), targetState);
        }

        boolean pregnancyPromptApplicable = isPregnancyPromptApplicable(encounter.patientId());
        UUID triageAssessmentId = UUID.randomUUID();

        // Step 1: Insert VitalSigns (context = TRIAGE).
        clinicalFacade.recordTriageVitalSigns(
                command.encounterId(), encounter.patientId(), triageAssessmentId,
                command.temperatureC(), command.systolicMmHg(), command.diastolicMmHg(), command.pulseBpm(),
                command.respiratoryRate(), command.spo2Pct(), command.weightKg(), command.heightCm(),
                command.avpu(), command.bloodGlucoseMmol(), command.actorUserId(), command.occurredAt());

        // Step 2: Insert TriageAssessment.
        TriageAssessment toInsert = TriageAssessment.create(
                command.encounterId(), command.chiefComplaint(), command.currentSymptoms(),
                command.allergies(), command.comorbidities(), command.modeOfAmbulation(),
                command.hasPain(), command.painScore(), command.painLocation(),
                command.fallRisk(), command.fallScore(), command.infectionRisk(),
                pregnancyPromptApplicable, command.pregnancyStatus(), command.gestationalWeeks(),
                command.priority(), command.immediateReason(), command.immediateReasonOtherText(),
                command.notes(), command.supersedesId(), command.actorUserId()
        ).withId(triageAssessmentId);
        TriageAssessment savedAssessment = triageAssessmentRepository.insert(toInsert);

        // Step 3: Append AllergyRecord to the patient, source = TRIAGE. Never overwrites.
        if (command.allergies() != null) {
            clinicalFacade.appendAllergyRecord(
                    encounter.patientId(), command.encounterId(), command.allergies(), command.actorUserId(), "TRIAGE");
        }

        // Step 4: Update Encounter — assigned_provider_id, type = EMERGENCY
        // when IMMEDIATE, department_id untouched. Optimistically locked.
        EncounterType type = immediate ? EncounterType.EMERGENCY : encounter.type();
        encounterRepository.assignProviderAndType(
                command.encounterId(), command.assignedProviderId(), type, command.expectedEncounterVersion());

        // Step 5: PaymentBypass when IMMEDIATE, source = TRIAGE_IMMEDIATE.
        if (immediate) {
            billingFacade.createImmediateTriagePaymentBypass(
                    command.encounterId(), immediateBypassReason(savedAssessment), command.actorUserId());
        }

        // Step 6: Post the consultation charge. Inside this transaction —
        // if pricing fails (unconfigured payer category, or a provider
        // holding neither DOCTOR nor SPECIALIST_DOCTOR), everything above
        // rolls back with it. 02-billing-architecture.md §4.1: "a triaged
        // patient with no charge walks straight past the cashier" — never
        // possible, not because every caller remembers, but because there
        // is no path from AWAITING_TRIAGE to a transition without this.
        billingFacade.postConsultationCharge(command.encounterId(), encounter.patientId(), encounter.facilityId(),
                encounter.payerType(), triageAssessmentId, command.assignedProviderId(), command.actorUserId());

        // Step 7: Transition — AWAITING_PAYMENT, or AWAITING_CONSULT when bypassed.
        encounterRepository.updateState(command.encounterId(), targetState);

        // Step 8: EncounterEvent.
        encounterRepository.insertEvent(new EncounterEvent(
                null, command.encounterId(), encounter.state(), targetState, command.actorUserId(), null, null));

        // Step 9: Publish TriageCompleted through the outbox.
        eventPublisher.publish("TriageCompleted", Map.of(
                "encounterId", command.encounterId().toString(),
                "patientId", encounter.patientId().toString(),
                "triageAssessmentId", savedAssessment.id().toString(),
                "priority", command.priority().name(),
                "assignedProviderId", command.assignedProviderId().toString()));

        return savedAssessment;
    }

    private boolean isPregnancyPromptApplicable(UUID patientId) {
        PatientDemographics demographics = patientFacade.findDemographics(patientId)
                .orElseThrow(() -> new NoSuchElementException("Patient not found: " + patientId));
        int minAgeYears = (int) protocolSettingReader.getDouble("triage.pregnancy_prompt.min_age_years");
        int maxAgeYears = (int) protocolSettingReader.getDouble("triage.pregnancy_prompt.max_age_years");
        return PregnancyPromptPolicy.isApplicable(
                demographics.gender(), demographics.dateOfBirth(), LocalDate.now(clock), minAgeYears, maxAgeYears);
    }

    private String immediateBypassReason(TriageAssessment assessment) {
        if (assessment.immediateReason() == ImmediateReason.OTHER) {
            return assessment.immediateReasonOtherText();
        }
        return assessment.immediateReason().name();
    }
}
