package com.adags.modules.inpatient.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.BedDayCalculator;
import com.adags.modules.inpatient.domain.DischargeNotAuthorizedException;
import com.adags.modules.inpatient.domain.DischargeNotOrderedException;
import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 04-procedure-bedday-rules.md §3.6/§3.7: the second half of discharge —
// records actual departure, computes the gap-adjusted billing stop, frees
// the bed, and transitions the encounter to its terminal state. ⚠ THE
// INVARIANT (17-ward-nurse.md ✗1, 02-billing-architecture.md §8): nothing
// here can be blocked by an unpaid balance — billingFacade.createReceivablesForOutstandingBalance
// only ever records the debt, and is called after the encounter is
// already DISCHARGED, not as a precondition of it.
@Service
public class CompleteDischargeService {

    private final AdmissionRepository admissionRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final NursingAssignmentRepository nursingAssignmentRepository;
    private final AuthorizationService authorizationService;
    private final EncounterFacade encounterFacade;
    private final BillingFacade billingFacade;
    private final WardChargeAccrualService wardChargeAccrualService;
    private final Clock clock;

    public CompleteDischargeService(
            AdmissionRepository admissionRepository,
            BedAssignmentRepository bedAssignmentRepository,
            NursingAssignmentRepository nursingAssignmentRepository,
            AuthorizationService authorizationService,
            EncounterFacade encounterFacade,
            BillingFacade billingFacade,
            WardChargeAccrualService wardChargeAccrualService,
            Clock clock
    ) {
        this.admissionRepository = admissionRepository;
        this.bedAssignmentRepository = bedAssignmentRepository;
        this.nursingAssignmentRepository = nursingAssignmentRepository;
        this.authorizationService = authorizationService;
        this.encounterFacade = encounterFacade;
        this.billingFacade = billingFacade;
        this.wardChargeAccrualService = wardChargeAccrualService;
        this.clock = clock;
    }

    @Transactional
    public Admission complete(CompleteDischargeCommand command) {
        Admission admission = admissionRepository.findById(command.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + command.admissionId()));
        if (admission.dischargeOrderedAt() == null) {
            throw new DischargeNotOrderedException(command.admissionId());
        }
        requireAuthorized(command.actorUserId(), command.admissionId());

        // Throws InvalidEncounterTransitionException if the encounter is
        // not DISCHARGE_ORDERED — the cheap guard, checked before any of
        // the billing/bed work below.
        encounterFacade.completeDischarge(admission.encounterId(), command.actorUserId());

        Instant actualDepartureAt = command.actualDepartureAt() != null ? command.actualDepartureAt() : Instant.now(clock);
        Instant billingStop = BedDayCalculator.billingStop(admission.dischargeOrderedAt(), actualDepartureAt);

        wardChargeAccrualService.reconcileForDischarge(admission.id(), billingStop, command.actorUserId());

        BedAssignment active = bedAssignmentRepository.findActiveByAdmissionId(admission.id())
                .orElseThrow(() -> new NoSuchElementException(
                        "Admission has no active bed assignment to release: " + admission.id()));
        bedAssignmentRepository.release(active.id(), actualDepartureAt);

        Admission updated = admissionRepository.recordActualDeparture(admission.id(), actualDepartureAt, admission.version());

        // Never a precondition of the transition above — root CLAUDE.md
        // "Billing": there is no code path where money prevents a patient
        // leaving. This only records what's left owing, after the fact.
        billingFacade.createReceivablesForOutstandingBalance(admission.encounterId());

        return updated;
    }

    private void requireAuthorized(UUID actorUserId, UUID admissionId) {
        Actor actor = new Actor(actorUserId);
        if (authorizationService.hasPermission(actor, "ADMISSION_ORDER")) {
            return;
        }
        if (nursingAssignmentRepository.existsForNurseAndAdmission(admissionId, actorUserId)) {
            return;
        }
        throw new DischargeNotAuthorizedException(actorUserId, admissionId);
    }
}
