package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.MarkAdministrationCommand;
import com.adags.modules.inpatient.application.MarkAdministrationService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.MedicationAdministration;
import com.adags.modules.inpatient.domain.MedicationAdministrationRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 17-ward-nurse.md §5: the MAR — generated slots the nurse marks, never
// typed. requirePermission mirrors BedController's inline pattern (no
// shared @RequiresPermission annotation exists in this codebase).
@RestController
@RequestMapping("/api/admissions/{admissionId}/mar")
public class MedicationAdministrationController {

    private final MarkAdministrationService markAdministrationService;
    private final MedicationAdministrationRepository medicationAdministrationRepository;
    private final AdmissionRepository admissionRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public MedicationAdministrationController(
            MarkAdministrationService markAdministrationService,
            MedicationAdministrationRepository medicationAdministrationRepository,
            AdmissionRepository admissionRepository,
            AuthorizationService authorizationService,
            Clock clock
    ) {
        this.markAdministrationService = markAdministrationService;
        this.medicationAdministrationRepository = medicationAdministrationRepository;
        this.admissionRepository = admissionRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @GetMapping
    public List<MedicationAdministrationResponse> list(@PathVariable UUID admissionId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "WARD_PATIENT_READ", admissionId);
        return medicationAdministrationRepository.findByAdmissionId(admissionId).stream()
                .map(MedicationAdministrationResponse::from).toList();
    }

    // 17-ward-nurse.md §11: "An unmarked slot past its due time is OVERDUE
    // and visible — not absent." This is that view.
    @GetMapping("/overdue")
    public List<MedicationAdministrationResponse> overdue(@PathVariable UUID admissionId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "WARD_PATIENT_READ", admissionId);
        return medicationAdministrationRepository.findOverdueByAdmissionId(admissionId, Instant.now(clock)).stream()
                .map(MedicationAdministrationResponse::from).toList();
    }

    @PostMapping("/{id}/mark")
    public MedicationAdministrationResponse mark(
            @PathVariable UUID admissionId, @PathVariable UUID id,
            @Valid @RequestBody MarkAdministrationRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "MEDICATION_ADMINISTER", admissionId);
        if (request.witnessUserId() != null) {
            requirePermission(actor, "MEDICATION_WITNESS", admissionId);
        }

        MedicationAdministration marked = markAdministrationService.mark(new MarkAdministrationCommand(
                id, request.status(), actor.userId(), request.doseGiven(), request.batchId(),
                request.witnessUserId(), request.omissionReason(), request.omissionNotes(), request.remarks()));
        return MedicationAdministrationResponse.from(marked);
    }

    private void requirePermission(Actor actor, String permissionCode, UUID admissionId) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + admissionId));
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(ScopeType.FACILITY, admission.facilityId()))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
