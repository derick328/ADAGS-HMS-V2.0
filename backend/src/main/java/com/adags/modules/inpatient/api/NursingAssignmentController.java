package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.NursingAssignment;
import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.kernel.PatientIdentity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 17-ward-nurse.md §11: "My History — read-only assignment history for the
// logged-in nurse." Unscoped by ward — it is the nurse's own roster across
// every ward she has worked, the same "my own data" shape as
// StoreController#mine, not a per-ward browse.
@RestController
@RequestMapping("/api/nursing-assignments")
public class NursingAssignmentController {

    private final NursingAssignmentRepository nursingAssignmentRepository;
    private final AdmissionRepository admissionRepository;
    private final PatientFacade patientFacade;
    private final AuthorizationService authorizationService;

    public NursingAssignmentController(
            NursingAssignmentRepository nursingAssignmentRepository,
            AdmissionRepository admissionRepository,
            PatientFacade patientFacade,
            AuthorizationService authorizationService
    ) {
        this.nursingAssignmentRepository = nursingAssignmentRepository;
        this.admissionRepository = admissionRepository;
        this.patientFacade = patientFacade;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/mine")
    public List<NursingAssignmentHistoryRowResponse> mine(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "WARD_PATIENT_READ")) {
            throw new AccessDeniedException("Missing permission: WARD_PATIENT_READ");
        }
        return nursingAssignmentRepository.findByNurseId(actor.userId()).stream()
                .map(this::toRow)
                .toList();
    }

    private NursingAssignmentHistoryRowResponse toRow(NursingAssignment assignment) {
        Admission admission = admissionRepository.findById(assignment.admissionId()).orElse(null);
        PatientIdentity identity = admission == null
                ? null : patientFacade.findIdentity(admission.patientId()).orElse(null);
        return new NursingAssignmentHistoryRowResponse(
                assignment.id(), assignment.admissionId(),
                admission == null ? null : admission.encounterId(),
                admission == null ? null : admission.patientId(),
                identity == null ? null : identity.fullName(),
                identity == null ? null : identity.hospitalNumber(),
                assignment.shift(), assignment.assignmentDate(), assignment.createdAt());
    }
}
