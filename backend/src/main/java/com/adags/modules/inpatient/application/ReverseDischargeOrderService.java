package com.adags.modules.inpatient.application;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.DischargeNotOrderedException;
import com.adags.modules.inpatient.domain.MissingReversalReasonException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 04-procedure-bedday-rules.md §3.9: "Cancel discharge order -> reason
// required -> discharge_ordered_at cleared -> billing resumes
// continuously from admission -> recorded in the encounter event log."
// Billing "resuming" needs no explicit action here — WardChargeAccrualService
// never paused for an ordered-but-not-completed discharge (it tracks bed
// occupancy, not this field), so clearing the order is the whole fix.
@Service
public class ReverseDischargeOrderService {

    private final AdmissionRepository admissionRepository;
    private final EncounterFacade encounterFacade;

    public ReverseDischargeOrderService(AdmissionRepository admissionRepository, EncounterFacade encounterFacade) {
        this.admissionRepository = admissionRepository;
        this.encounterFacade = encounterFacade;
    }

    @Transactional
    public Admission reverse(ReverseDischargeOrderCommand command) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new MissingReversalReasonException();
        }

        Admission admission = admissionRepository.findById(command.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + command.admissionId()));
        if (admission.dischargeOrderedAt() == null) {
            throw new DischargeNotOrderedException(command.admissionId());
        }

        // Throws InvalidEncounterTransitionException if the encounter is not DISCHARGE_ORDERED.
        encounterFacade.reverseDischargeOrder(admission.encounterId(), command.actorUserId(), command.reason());

        return admissionRepository.reverseDischargeOrder(command.admissionId(), admission.version());
    }
}
