package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// 07-doctor.md §7's Admit-to-ward nurse picker: the doctor sees every nurse
// in this facility and how loaded each one currently is, so they don't
// pile a fifth patient onto someone already carrying four. Read-only —
// the actual assignment goes through /api/admissions/{id}/nursing-assignments.
// Gated on ADMISSION_ORDER (the doctor's own permission to admit), so the
// endpoint only opens to people who could use the workload information —
// same discipline as StoreController's "the read is scoped by the write it
// serves".
@RestController
@RequestMapping("/api/nurses")
public class NurseWorkloadController {

    private final NursingAssignmentRepository nursingAssignmentRepository;
    private final AuthorizationService authorizationService;

    public NurseWorkloadController(
            NursingAssignmentRepository nursingAssignmentRepository,
            AuthorizationService authorizationService
    ) {
        this.nursingAssignmentRepository = nursingAssignmentRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/workload")
    public List<NurseWorkloadResponse> workload(
            @RequestParam UUID facilityId, @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "ADMISSION_ORDER")) {
            throw new AccessDeniedException("Missing permission: ADMISSION_ORDER");
        }
        return nursingAssignmentRepository.countActiveAdmissionsPerNurse(facilityId).stream()
                .map(w -> new NurseWorkloadResponse(w.nurseId(), w.username(), w.activePatients()))
                .toList();
    }
}
