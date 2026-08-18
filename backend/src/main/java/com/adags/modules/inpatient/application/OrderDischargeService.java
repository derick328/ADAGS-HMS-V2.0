package com.adags.modules.inpatient.application;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.DischargeNotAuthorizedException;
import com.adags.modules.inpatient.domain.DischargeOrdererRole;
import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 04-procedure-bedday-rules.md §3.6: "Doctor, OR the nurse that doctor
// assigned to this patient... scoped to the ASSIGNMENT, not a blanket
// ward-nurse permission." The controller checks the base DISCHARGE_ORDER
// permission+scope; this is the finer check that a nurse holding that
// permission is actually on THIS admission's nursing roster — the
// permission alone is not enough for her, same reasoning as
// 17-ward-nurse.md §1 ⚠1's care-relationship check.
@Service
public class OrderDischargeService {

    private final AdmissionRepository admissionRepository;
    private final NursingAssignmentRepository nursingAssignmentRepository;
    private final AuthorizationService authorizationService;
    private final EncounterFacade encounterFacade;
    private final Clock clock;

    public OrderDischargeService(
            AdmissionRepository admissionRepository,
            NursingAssignmentRepository nursingAssignmentRepository,
            AuthorizationService authorizationService,
            EncounterFacade encounterFacade,
            Clock clock
    ) {
        this.admissionRepository = admissionRepository;
        this.nursingAssignmentRepository = nursingAssignmentRepository;
        this.authorizationService = authorizationService;
        this.encounterFacade = encounterFacade;
        this.clock = clock;
    }

    @Transactional
    public Admission order(OrderDischargeCommand command) {
        Admission admission = admissionRepository.findById(command.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + command.admissionId()));

        DischargeOrdererRole ordererRole = determineOrdererRole(command.actorUserId(), command.admissionId());

        // Throws InvalidEncounterTransitionException if the encounter is not IN_WARD.
        encounterFacade.orderDischarge(admission.encounterId(), command.actorUserId());

        return admissionRepository.orderDischarge(command.admissionId(), Instant.now(clock),
                command.actorUserId(), ordererRole, admission.version());
    }

    // ADMISSION_ORDER is doctor-exclusive per the permission catalogue
    // (V5) — held as a permission check, not a role-name check (root
    // CLAUDE.md "Authorisation"), the same proxy AssignBedService's sibling
    // logic in step 1 used to tell doctor and nurse paths apart.
    private DischargeOrdererRole determineOrdererRole(UUID actorUserId, UUID admissionId) {
        Actor actor = new Actor(actorUserId);
        if (authorizationService.hasPermission(actor, "ADMISSION_ORDER")) {
            return DischargeOrdererRole.DOCTOR;
        }
        if (nursingAssignmentRepository.existsForNurseAndAdmission(admissionId, actorUserId)) {
            return DischargeOrdererRole.NURSE;
        }
        throw new DischargeNotAuthorizedException(actorUserId, admissionId);
    }
}
