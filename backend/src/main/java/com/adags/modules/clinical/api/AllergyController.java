package com.adags.modules.clinical.api;

import com.adags.modules.clinical.application.ReconcileAllergyRecordService;
import com.adags.modules.clinical.domain.AllergyConfirmationStatus;
import com.adags.modules.clinical.domain.AllergyRecord;
import com.adags.modules.clinical.domain.AllergyRecordRepository;
import com.adags.modules.clinical.domain.AllergySource;
import com.adags.modules.identity.IdentityFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.CareRelationshipPort;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 07-doctor.md §6, root CLAUDE.md "Safety": allergy read/create/reconcile.
// §6.1 ⚠: UNCONFIRMED and REFUTED render identically to CONFIRMED
// everywhere — this controller never filters by confirmationStatus, only
// returns full history (AllergyRecordRepository#findByPatientId already
// guarantees that). The confirm/refute action is "documentary, not
// functional" (ReconcileAllergyRecordService) — it never changes what this
// GET returns beyond the metadata itself.
@RestController
public class AllergyController {

    private final AllergyRecordRepository allergyRecordRepository;
    private final ReconcileAllergyRecordService reconcileAllergyRecordService;
    private final IdentityFacade identityFacade;
    private final AuthorizationService authorizationService;
    private final CareRelationshipPort careRelationship;
    private final AuditRecorder historyAudit;

    public AllergyController(
            AllergyRecordRepository allergyRecordRepository,
            ReconcileAllergyRecordService reconcileAllergyRecordService,
            IdentityFacade identityFacade,
            AuthorizationService authorizationService,
            CareRelationshipPort careRelationship,
            AuditRecorder historyAudit
    ) {
        this.allergyRecordRepository = allergyRecordRepository;
        this.reconcileAllergyRecordService = reconcileAllergyRecordService;
        this.identityFacade = identityFacade;
        this.authorizationService = authorizationService;
        this.careRelationship = careRelationship;
        this.historyAudit = historyAudit;
    }

    @GetMapping("/api/patients/{patientId}/allergies")
    public List<AllergyRecordResponse> byPatient(@PathVariable UUID patientId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "ALLERGY_READ");
        if (authorizationService.hasPermission(actor, "CONSULTATION_CREATE")
                && !careRelationship.hasCareRelationship(actor.userId(), patientId)) {
            throw new AccessDeniedException("An active care relationship is required for clinical history.");
        }
        historyAudit.record(actor.userId(), "ALLERGY_HISTORY_READ", "patient", patientId, patientId);
        return allergyRecordRepository.findByPatientId(patientId).stream()
                .map(this::toResponse)
                .toList();
    }

    // §6.2's "Add allergy" — always a new row, never an edit of an
    // existing one (root CLAUDE.md "Data"). source is always DOCTOR here;
    // TRIAGE-sourced rows come from the triage module's own write path.
    @PostMapping("/api/patients/{patientId}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    public AllergyRecordResponse create(
            @PathVariable UUID patientId, @Valid @RequestBody CreateAllergyRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "ALLERGY_CREATE");
        AllergyRecord saved = allergyRecordRepository.insert(AllergyRecord.record(
                patientId, request.encounterId(), request.description(), request.reaction(), request.severity(),
                AllergySource.DOCTOR, actor.userId(), request.supersedesId()));
        return toResponse(saved);
    }

    // §6.1: "refuting requires a note" — enforced by ReconcileAllergyRecordService
    // and by the allergy_record CHECK constraint independently.
    @PostMapping("/api/allergy-records/{id}/reconcile")
    public AllergyRecordResponse reconcile(
            @PathVariable UUID id, @Valid @RequestBody ReconcileAllergyRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "ALLERGY_CONFIRM");
        reconcileAllergyRecordService.reconcile(id, request.decision(), actor.userId(), request.confirmationNote());
        AllergyRecord updated = allergyRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("AllergyRecord not found: " + id));
        return toResponse(updated);
    }

    private AllergyRecordResponse toResponse(AllergyRecord record) {
        String recordedByName = identityFacade.findUsername(record.recordedBy()).orElse("unknown");
        return AllergyRecordResponse.from(record, recordedByName);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }

    public record CreateAllergyRequest(
            UUID encounterId, @NotBlank String description, String reaction, String severity, UUID supersedesId
    ) {
    }

    public record ReconcileAllergyRequest(@NotNull AllergyConfirmationStatus decision, String confirmationNote) {
    }
}
