package com.adags.modules.encounters.api;

import com.adags.modules.encounters.application.CloseEncounterCommand;
import com.adags.modules.encounters.application.CloseEncounterService;
import com.adags.modules.encounters.application.MoveToAwaitingConsultService;
import com.adags.modules.encounters.application.OpenEncounterCommand;
import com.adags.modules.encounters.application.OpenEncounterService;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 05-receptionist.md §8: GET /encounters?state=&date= — the "here now"
// panel projection — and every transition beyond open/dismiss (triage,
// payment, consult, ...) are out of this slice — no module exists yet to
// drive them. See backend/CLAUDE.md for the full transition graph.
//
// The list endpoint is deliberately unscoped by department (unlike
// open/close/getById below) — a receptionist desk works across the whole
// facility's "here now" panel, not one department at a time; ENCOUNTER_OPEN
// is checked as a plain hold-it-or-not, visibility-only in the sense every
// permission check is (usePermission's own contract on the frontend).
@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final OpenEncounterService openEncounterService;
    private final CloseEncounterService closeEncounterService;
    private final MoveToAwaitingConsultService moveToAwaitingConsultService;
    private final EncounterRepository encounterRepository;
    private final AuthorizationService authorizationService;

    public EncounterController(
            OpenEncounterService openEncounterService,
            CloseEncounterService closeEncounterService,
            MoveToAwaitingConsultService moveToAwaitingConsultService,
            EncounterRepository encounterRepository,
            AuthorizationService authorizationService
    ) {
        this.openEncounterService = openEncounterService;
        this.closeEncounterService = closeEncounterService;
        this.moveToAwaitingConsultService = moveToAwaitingConsultService;
        this.encounterRepository = encounterRepository;
        this.authorizationService = authorizationService;
    }

    // Three callers share this list, each holding a different one of these
    // permissions rather than all three: the receptionist's "here now" panel
    // (ENCOUNTER_OPEN), the triage nurse's queue, 06-triage.md §1/§3,
    // filtered to states=AWAITING_TRIAGE (TRIAGE_QUEUE_READ), and the
    // doctor's "My Queue", 07-doctor.md §2, filtered to
    // states=AWAITING_CONSULT (CONSULTATION_CREATE — DOCTOR/
    // SPECIALIST_DOCTOR hold this but neither ENCOUNTER_OPEN nor
    // TRIAGE_QUEUE_READ, V5__full_permission_catalogue.sql). Filtering to
    // "assigned to me" is the caller's own job (client-side, same as
    // triage's queue), same reasoning as excludeTerminal/date bounds
    // already being optional narrowing rather than mandatory scoping.
    @GetMapping
    public List<EncounterResponse> search(
            @RequestParam(required = false) List<EncounterState> states,
            @RequestParam(defaultValue = "false") boolean excludeTerminal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @AuthenticationPrincipal Actor actor
    ) {
        requireAny(actor, "ENCOUNTER_OPEN", "TRIAGE_QUEUE_READ", "CONSULTATION_CREATE");
        return encounterRepository.search(states, excludeTerminal, createdFrom, createdTo).stream()
                .map(EncounterResponse::from)
                .toList();
    }

    // 08-doctor-navigation.md §3.3: "My Patients" patient-detail timeline —
    // every encounter for this patient, across every clinician (continuity
    // requires seeing colleagues' visits too, not just this doctor's own).
    // Unlike PatientController#getById (genuinely unscoped — Patient carries
    // no facility_id at all), Encounter DOES carry department_id/facility_id
    // on every row (root CLAUDE.md "Facility"), so this filters per-row to
    // encounters the actor's own scoped assignment actually covers, rather
    // than a single blanket permission check — a facility/global-scoped
    // doctor still sees the full continuity-of-care timeline, a department-
    // scoped one only sees the encounters within their own department
    // (root CLAUDE.md "Authorisation": "a ward-scoped user must not see the
    // same-named ward at another facility").
    @GetMapping("/by-patient/{patientId}")
    public List<EncounterResponse> byPatient(@PathVariable UUID patientId, @AuthenticationPrincipal Actor actor) {
        return encounterRepository.findByPatientId(patientId).stream()
                .filter(encounter -> authorizationService.hasAnyPermission(actor, ScopeRef.of(ScopeType.DEPARTMENT, encounter.departmentId()),
                        "PATIENT_READ_VISIT_HISTORY", "ENCOUNTER_OPEN", "CONSULTATION_CREATE"))
                .map(EncounterResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EncounterResponse open(@Valid @RequestBody OpenEncounterRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "ENCOUNTER_OPEN", ScopeType.DEPARTMENT, request.departmentId());
        OpenEncounterCommand command = new OpenEncounterCommand(
                request.patientId(), request.departmentId(), request.appointmentId(), actor.userId());
        Encounter encounter = openEncounterService.checkIn(command);
        return EncounterResponse.from(encounter);
    }

    // 02-billing-architecture.md §11 ⚠: "default to the open encounter when
    // there is one" — the invoice-creation screen's own lookup. Returns
    // null (200, empty body) rather than 404 for "none" — this is a normal,
    // expected outcome for a standalone invoice, not an error case.
    @GetMapping("/open-for-patient/{patientId}")
    public EncounterResponse openForPatient(@PathVariable UUID patientId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "ENCOUNTER_OPEN");
        return encounterRepository.findOpenByPatientId(patientId).map(EncounterResponse::from).orElse(null);
    }

    @GetMapping("/{id}")
    public EncounterResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Encounter encounter = encounterRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + id));
        requireAny(actor, ScopeRef.of(ScopeType.DEPARTMENT, encounter.departmentId()), "ENCOUNTER_OPEN", "CONSULTATION_CREATE");
        return EncounterResponse.from(encounter);
    }

    @PostMapping("/{id}/close")
    public EncounterResponse close(
            @PathVariable UUID id,
            @Valid @RequestBody CloseEncounterRequest request,
            @AuthenticationPrincipal Actor actor
    ) {
        Encounter encounter = encounterRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + id));
        requirePermission(actor, "ENCOUNTER_CLOSE_ADMIN", ScopeType.DEPARTMENT, encounter.departmentId());
        CloseEncounterCommand command = new CloseEncounterCommand(id, request.outcome(), request.reason(), actor.userId());
        Encounter closed = closeEncounterService.close(command);
        return EncounterResponse.from(closed);
    }

    // 05a-receptionist-decisions.md §3.3: "Open encounter match → resume it,
    // transition AWAITING_RESULTS → AWAITING_CONSULT, back to the doctor's
    // queue. No charge, no triage." MoveToAwaitingConsultService already
    // does exactly this — its billing-gate re-check is a no-op here because
    // the consultation line durably carries its authorization from the
    // first payment (root CLAUDE.md "Billing": authorization_id is never
    // cleared), so this call cannot re-block an already-paid patient.
    @PostMapping("/{id}/resume")
    public EncounterResponse resume(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Encounter encounter = encounterRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + id));
        requirePermission(actor, "ENCOUNTER_OPEN", ScopeType.DEPARTMENT, encounter.departmentId());
        return EncounterResponse.from(moveToAwaitingConsultService.moveToAwaitingConsult(id, actor.userId()));
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }

    private void requireAny(Actor actor, String... permissionCodes) {
        if (!authorizationService.hasAnyPermission(actor, permissionCodes)) {
            throw new AccessDeniedException("Missing any of: " + String.join(", ", permissionCodes));
        }
    }

    private void requireAny(Actor actor, ScopeRef targetScope, String... permissionCodes) {
        if (!authorizationService.hasAnyPermission(actor, targetScope, permissionCodes)) {
            throw new AccessDeniedException("Missing any of: " + String.join(", ", permissionCodes));
        }
    }
}
