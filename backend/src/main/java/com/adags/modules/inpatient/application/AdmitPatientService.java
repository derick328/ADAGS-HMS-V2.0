package com.adags.modules.inpatient.application;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WardStatus;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

// 07-doctor.md §7's ADMITTED disposition, deliberately not folded into
// FinalizeConsultationService/DispositionOutcome — that service's own
// comment excludes ADMITTED because handling it needs the inpatient
// module's own state (the Admission row), which encounters must not import.
//
// Order: the encounter transition guard runs first (cheap, no side effects,
// and it's what lets an admit attempt on a not-IN_CONSULT encounter surface
// InvalidEncounterTransitionException rather than a confusing "no open
// consultation record" from the finalize call that follows). Both are
// still inside this method's own @Transactional — if finalize or the
// admission insert fails afterward, the encounter's ADMITTED update rolls
// back with everything else in the same transaction.
@Service
public class AdmitPatientService {

    private final EncounterFacade encounterFacade;
    private final ClinicalFacade clinicalFacade;
    private final AdmissionRepository admissionRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public AdmitPatientService(
            EncounterFacade encounterFacade,
            ClinicalFacade clinicalFacade,
            AdmissionRepository admissionRepository,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.encounterFacade = encounterFacade;
        this.clinicalFacade = clinicalFacade;
        this.admissionRepository = admissionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Admission admit(AdmitPatientCommand command) {
        EncounterContext context = encounterFacade.findContext(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        // Throws InvalidEncounterTransitionException if the encounter is not IN_CONSULT.
        encounterFacade.admit(command.encounterId(), command.admittingDoctorId());

        // Throws InvalidConsultationRecordException / NoSuchElementException
        // if the note isn't ready to finalize — the @Transactional rollback
        // above undoes the ADMITTED update in that case.
        clinicalFacade.finalizeConsultationRecord(
                command.encounterId(), command.admittingDoctorId(),
                command.retrospectiveCompletion(), command.retrospectiveNote());

        Admission toInsert = new Admission(null, command.encounterId(), context.patientId(), context.facilityId(),
                command.admittingDoctorId(), command.orderedWardClass(), Instant.now(clock),
                null, null, null, null, WardStatus.ADMITTED, null, null, null, 0, null);
        Admission saved = admissionRepository.insert(toInsert);

        eventPublisher.publish("PatientAdmitted", Map.of(
                "admissionId", saved.id().toString(),
                "encounterId", command.encounterId().toString(),
                "patientId", context.patientId().toString(),
                "orderedWardClass", command.orderedWardClass().name()));

        return saved;
    }
}
