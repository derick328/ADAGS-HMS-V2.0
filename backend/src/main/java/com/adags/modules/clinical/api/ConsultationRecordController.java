package com.adags.modules.clinical.api;

import com.adags.modules.clinical.application.AddDiagnosisService;
import com.adags.modules.clinical.application.AutosaveConsultationRecordCommand;
import com.adags.modules.clinical.application.AutosaveConsultationRecordService;
import com.adags.modules.clinical.application.IncompleteConsultationService;
import com.adags.modules.clinical.application.VoidDiagnosisService;
import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.Diagnosis;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.clinical.domain.DiagnosisRepository;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 07-doctor.md §4/§5/§10, 09-doctor-incomplete-consultations.md §2-§5. The
// non-encounter-state half of the consultation lifecycle — see
// encounters/api/ConsultationController's class comment for the split.
// Reads within this module use the repositories directly rather than
// ClinicalFacade (backend/CLAUDE.md "Module boundary rules" #1 restricts
// what OTHER modules may import, not what a controller inside this module
// may call).
@RestController
public class ConsultationRecordController {

    private final ConsultationRecordRepository consultationRecordRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final AutosaveConsultationRecordService autosaveService;
    private final AddDiagnosisService addDiagnosisService;
    private final VoidDiagnosisService voidDiagnosisService;
    private final IncompleteConsultationService incompleteConsultationService;
    private final EncounterFacade encounterFacade;
    private final AuthorizationService authorizationService;
    private final com.adags.shared.authz.AuditRecorder historyAudit;

    public ConsultationRecordController(
            ConsultationRecordRepository consultationRecordRepository,
            DiagnosisRepository diagnosisRepository,
            AutosaveConsultationRecordService autosaveService,
            AddDiagnosisService addDiagnosisService,
            VoidDiagnosisService voidDiagnosisService,
            IncompleteConsultationService incompleteConsultationService,
            EncounterFacade encounterFacade,
            AuthorizationService authorizationService,
            com.adags.shared.authz.AuditRecorder historyAudit
    ) {
        this.consultationRecordRepository = consultationRecordRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.autosaveService = autosaveService;
        this.addDiagnosisService = addDiagnosisService;
        this.voidDiagnosisService = voidDiagnosisService;
        this.incompleteConsultationService = incompleteConsultationService;
        this.encounterFacade = encounterFacade;
        this.authorizationService = authorizationService;
        this.historyAudit = historyAudit;
    }

    // §10: "Autosave server-side every 20 seconds keyed to (encounter_id,
    // author_id)." Author-checked here (not just "record exists and is
    // OPEN") so one doctor's autosave loop can never overwrite another's
    // draft — the record id alone doesn't carry that guarantee.
    @PutMapping("/api/consultation-records/{id}/autosave")
    public ConsultationRecordResponse autosave(
            @PathVariable UUID id, @RequestBody AutosaveConsultationRecordRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "CONSULTATION_CREATE");
        ConsultationRecord record = requireOwnRecord(id, actor.userId());

        autosaveService.autosave(new AutosaveConsultationRecordCommand(
                id, request.historyOfPresentingIllness(), request.pastMedicalHistory(), request.pastSurgicalHistory(),
                request.comorbidities(), request.familySocialHistory(), request.physicalExamination(),
                request.clinicalNotes(), request.treatmentPlan()));

        return ConsultationRecordResponse.from(consultationRecordRepository.findById(record.id())
                .orElseThrow(() -> new NoSuchElementException("ConsultationRecord not found: " + id)));
    }

    // 08-doctor-navigation.md §3.3: "other clinicians' notes visible" —
    // every author's record for the encounter, not just the caller's own.
    @GetMapping("/api/encounters/{encounterId}/consultation-records")
    public List<ConsultationRecordResponse> byEncounter(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        requirePermissionForEncounter(actor, "CLINICAL_NOTE_READ", encounterId);
        return consultationRecordRepository.findByEncounterId(encounterId).stream()
                .map(ConsultationRecordResponse::from)
                .toList();
    }

    // §4.1: coded diagnosis, snapshotted title, one primary per encounter
    // (enforced by the idx_diagnosis_one_primary partial unique index —
    // AddDiagnosisService does not itself demote a previous primary, so a
    // second is_primary=true insert in the same encounter 409s at the DB,
    // same "concurrency is a constraint, not an app check" rule as every
    // other UNIQUE...WHERE in root CLAUDE.md).
    @PostMapping("/api/consultation-records/{id}/diagnoses")
    @ResponseStatus(HttpStatus.CREATED)
    public DiagnosisResponse addDiagnosis(
            @PathVariable UUID id, @Valid @RequestBody AddDiagnosisRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "DIAGNOSIS_CREATE");
        ConsultationRecord record = requireOwnRecord(id, actor.userId());

        Diagnosis diagnosis = addDiagnosisService.addDiagnosis(record.encounterId(), id, request.icd10Code(),
                request.freeTextClarifier(), request.certainty(), request.isPrimary(), actor.userId());
        return DiagnosisResponse.from(diagnosis);
    }

    @DeleteMapping("/api/consultation-records/{id}/diagnoses/{diagnosisId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void voidDiagnosis(@PathVariable UUID id, @PathVariable UUID diagnosisId,
                              @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "DIAGNOSIS_CREATE");
        requireOwnRecord(id, actor.userId());
        voidDiagnosisService.voidDiagnosis(id, diagnosisId, actor.userId());
    }

    @GetMapping("/api/encounters/{encounterId}/diagnoses")
    public List<DiagnosisResponse> diagnoses(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        requirePermissionForEncounter(actor, "DIAGNOSIS_READ", encounterId);
        return diagnosisRepository.findByEncounterId(encounterId).stream()
                .map(DiagnosisResponse::from)
                .toList();
    }

    // 09-doctor-incomplete-consultations.md §3/§4: own data only — the
    // login prompt and sidebar badge, never another doctor's backlog
    // (§8's aggregate-only governance view is a separate, later report).
    @GetMapping("/api/consultation-records/incomplete")
    public List<ConsultationRecordResponse> incomplete(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "CONSULTATION_CREATE");
        return incompleteConsultationService.findIncomplete(actor.userId()).stream()
                .map(ConsultationRecordResponse::from)
                .toList();
    }

    private ConsultationRecord requireOwnRecord(UUID id, UUID authorId) {
        ConsultationRecord record = consultationRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("ConsultationRecord not found: " + id));
        if (!record.authorId().equals(authorId)) {
            throw new AccessDeniedException("This consultation record belongs to another author.");
        }
        return record;
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }

    // root CLAUDE.md "Authorisation": scope resolution walks WARD ->
    // DEPARTMENT -> FACILITY — reads keyed by encounterId must check
    // against that encounter's own department, not just "holds the
    // permission somewhere," the same distinction EncounterController's
    // getById already draws for the encounter itself.
    private void requirePermissionForEncounter(Actor actor, String permissionCode, UUID encounterId) {
        EncounterContext context = encounterFacade.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (authorizationService.checkPatientAccess(actor, permissionCode, context.patientId(), ScopeRef.of(ScopeType.DEPARTMENT, context.departmentId())) != com.adags.shared.authz.PolicyResult.ALLOWED) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
        historyAudit.record(actor.userId(), "CLINICAL_HISTORY_READ", "encounter", encounterId, context.patientId());
    }

    public record AutosaveConsultationRecordRequest(
            String historyOfPresentingIllness, String pastMedicalHistory, String pastSurgicalHistory,
            String comorbidities, String familySocialHistory, String physicalExamination, String clinicalNotes,
            String treatmentPlan
    ) {
    }

    public record AddDiagnosisRequest(
            @NotBlank String icd10Code, String freeTextClarifier, @NotNull DiagnosisCertainty certainty, boolean isPrimary
    ) {
    }
}
