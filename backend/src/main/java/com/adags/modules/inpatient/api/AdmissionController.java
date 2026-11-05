package com.adags.modules.inpatient.api;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.application.AdmitPatientCommand;
import com.adags.modules.inpatient.application.AdmitPatientService;
import com.adags.modules.inpatient.application.AssignBedCommand;
import com.adags.modules.inpatient.application.AssignBedService;
import com.adags.modules.inpatient.application.AssignNurseCommand;
import com.adags.modules.inpatient.application.AssignNurseService;
import com.adags.modules.inpatient.application.CompleteDischargeCommand;
import com.adags.modules.inpatient.application.CompleteDischargeService;
import com.adags.modules.inpatient.application.OrderDischargeCommand;
import com.adags.modules.inpatient.application.OrderDischargeService;
import com.adags.modules.inpatient.application.ReverseDischargeOrderCommand;
import com.adags.modules.inpatient.application.ReverseDischargeOrderService;
import com.adags.modules.inpatient.application.UpdateWardStatusCommand;
import com.adags.modules.inpatient.application.UpdateWardStatusService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.NursingAssignment;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.PatientIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admissions")
public class AdmissionController {

    private final AdmitPatientService admitPatientService;
    private final AssignBedService assignBedService;
    private final AssignNurseService assignNurseService;
    private final OrderDischargeService orderDischargeService;
    private final ReverseDischargeOrderService reverseDischargeOrderService;
    private final CompleteDischargeService completeDischargeService;
    private final UpdateWardStatusService updateWardStatusService;
    private final AdmissionRepository admissionRepository;
    private final BedRepository bedRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final WardRepository wardRepository;
    private final EncounterFacade encounterFacade;
    private final PatientFacade patientFacade;
    private final AuthorizationService authorizationService;

    public AdmissionController(
            AdmitPatientService admitPatientService,
            AssignBedService assignBedService,
            AssignNurseService assignNurseService,
            OrderDischargeService orderDischargeService,
            ReverseDischargeOrderService reverseDischargeOrderService,
            CompleteDischargeService completeDischargeService,
            UpdateWardStatusService updateWardStatusService,
            AdmissionRepository admissionRepository,
            BedRepository bedRepository,
            BedAssignmentRepository bedAssignmentRepository,
            WardRepository wardRepository,
            EncounterFacade encounterFacade,
            PatientFacade patientFacade,
            AuthorizationService authorizationService
    ) {
        this.admitPatientService = admitPatientService;
        this.assignBedService = assignBedService;
        this.assignNurseService = assignNurseService;
        this.orderDischargeService = orderDischargeService;
        this.reverseDischargeOrderService = reverseDischargeOrderService;
        this.completeDischargeService = completeDischargeService;
        this.updateWardStatusService = updateWardStatusService;
        this.admissionRepository = admissionRepository;
        this.bedRepository = bedRepository;
        this.bedAssignmentRepository = bedAssignmentRepository;
        this.wardRepository = wardRepository;
        this.encounterFacade = encounterFacade;
        this.patientFacade = patientFacade;
        this.authorizationService = authorizationService;
    }

    // 17-ward-nurse.md §3: "Scope the ward patient list" — the Dashboard and
    // Ward Patients screens' single source list, one row per patient
    // currently on wardId. Scoped at WARD, same as BED_ASSIGN/
    // WARD_STATUS_UPDATE elsewhere in this controller — the wardId itself
    // is the resource, not derived from an admission the caller already
    // knows about.
    @GetMapping
    public List<WardPatientRowResponse> listByWard(@RequestParam UUID wardId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "WARD_PATIENT_READ", ScopeType.WARD, wardId);
        return admissionRepository.findByCurrentWardId(wardId).stream().map(this::toWardPatientRow).toList();
    }

    // 17-ward-nurse.md §8: patients the doctor marked ADMITTED who have not
    // yet been placed into a bed — they belong to no ward's roster
    // (listByWard needs an active bed assignment) and accrue no bed-days, so
    // they are otherwise invisible to every nurse and to billing. The claim
    // worklist: scoped to the facility of the nurse's own ward, gated on the
    // same BED_ASSIGN@ward she needs to then place one (a nurse who can
    // assign beds in the facility pulls a new admission into her ward).
    // Reuses WardPatientRowResponse — bed/ward fields come back null, which
    // is exactly what "not yet placed" means.
    @GetMapping("/awaiting-placement")
    public List<WardPatientRowResponse> awaitingPlacement(@RequestParam UUID wardId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "BED_ASSIGN", ScopeType.WARD, wardId);
        Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new NoSuchElementException("Ward not found: " + wardId));
        return admissionRepository.findAwaitingPlacement(ward.facilityId()).stream()
                .map(this::toWardPatientRow)
                .toList();
    }

    // 07-doctor.md §7/§11: the admitting doctor's own admitted patients — the
    // doctor-side "Admitted" worklist. Own data, so gated on ADMISSION_ORDER
    // unscoped (a doctor who can admit sees whom they admitted), the same
    // "/mine" shape as PrescriptionController#mine. Includes not-yet-placed
    // admissions; bed/ward come back null for those, as on the claim list.
    @GetMapping("/mine")
    public List<WardPatientRowResponse> mine(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "ADMISSION_ORDER")) {
            throw new AccessDeniedException("Missing permission: ADMISSION_ORDER");
        }
        return admissionRepository.findActiveByAdmittingDoctor(actor.userId()).stream()
                .map(this::toWardPatientRow)
                .toList();
    }

    private WardPatientRowResponse toWardPatientRow(Admission admission) {
        PatientIdentity identity = patientFacade.findIdentity(admission.patientId()).orElse(null);
        BedAssignment activeBed = bedAssignmentRepository.findActiveByAdmissionId(admission.id()).orElse(null);
        Bed bed = activeBed == null ? null : bedRepository.findById(activeBed.bedId()).orElse(null);
        return new WardPatientRowResponse(
                admission.id(), admission.encounterId(), admission.patientId(),
                identity == null ? null : identity.fullName(),
                identity == null ? null : identity.hospitalNumber(),
                bed == null ? null : bed.id(),
                bed == null ? null : bed.label(),
                admission.orderedWardClass(), admission.wardStatus(), admission.admittedAt(),
                admission.dischargeOrderedAt(), admission.wardStatusSuggestedCriticalAt());
    }

    // 07-doctor.md §7's ADMITTED disposition. Scoped to the encounter's
    // department — same pattern as EncounterController's other checks.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdmissionResponse admit(@Valid @RequestBody AdmitPatientRequest request, @AuthenticationPrincipal Actor actor) {
        EncounterContext context = encounterFacade.findContext(request.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + request.encounterId()));
        requirePermission(actor, "ADMISSION_ORDER", ScopeType.DEPARTMENT, context.departmentId());

        AdmitPatientCommand command = new AdmitPatientCommand(request.encounterId(), actor.userId(),
                request.orderedWardClass(), request.retrospectiveCompletion(), request.retrospectiveNote());
        Admission admission = admitPatientService.admit(command);
        return AdmissionResponse.from(admission);
    }

    @GetMapping("/{id}")
    public AdmissionResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Admission admission = admissionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + id));
        requirePermission(actor, "WARD_PATIENT_READ", ScopeType.FACILITY, admission.facilityId());
        return AdmissionResponse.from(admission);
    }

    // 17-ward-nurse.md §8.1/§8.3: first call moves the encounter into
    // IN_WARD; every later call on the same admission is a transfer.
    // Scoped to the target bed's ward.
    @PostMapping("/{id}/bed-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public BedAssignmentResponse assignBed(
            @PathVariable UUID id, @Valid @RequestBody AssignBedRequest request, @AuthenticationPrincipal Actor actor
    ) {
        Bed bed = bedRepository.findById(request.bedId())
                .orElseThrow(() -> new NoSuchElementException("Bed not found: " + request.bedId()));
        requirePermission(actor, "BED_ASSIGN", ScopeType.WARD, bed.wardId());

        AssignBedCommand command = new AssignBedCommand(id, request.bedId(), request.placementReason(), actor.userId());
        BedAssignment assignment = assignBedService.assign(command);
        return BedAssignmentResponse.from(assignment);
    }

    // 17-ward-nurse.md §8.2: care responsibility, not a blanket ward
    // permission — reuses BED_ASSIGN, scoped to the admission's current
    // ward, same as bed assignment itself.
    @PostMapping("/{id}/nursing-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public NursingAssignmentResponse assignNurse(
            @PathVariable UUID id, @Valid @RequestBody AssignNurseRequest request, @AuthenticationPrincipal Actor actor
    ) {
        BedAssignment currentBed = bedAssignmentRepository.findActiveByAdmissionId(id)
                .orElseThrow(() -> new NoSuchElementException("Admission has no active bed assignment: " + id));
        Bed bed = bedRepository.findById(currentBed.bedId())
                .orElseThrow(() -> new NoSuchElementException("Bed not found: " + currentBed.bedId()));
        requirePermission(actor, "BED_ASSIGN", ScopeType.WARD, bed.wardId());

        AssignNurseCommand command = new AssignNurseCommand(
                id, request.nurseId(), request.shift(), request.assignmentDate(), actor.userId());
        NursingAssignment assignment = assignNurseService.assign(command);
        return NursingAssignmentResponse.from(assignment);
    }

    // 04-procedure-bedday-rules.md §3.6. The base permission+scope check
    // here; OrderDischargeService itself enforces the finer
    // assignment-scoping for a nurse actor (this permission alone lets a
    // doctor holding it through, but a nurse must additionally be on the
    // roster — see that service).
    @PostMapping("/{id}/discharge-order")
    public AdmissionResponse orderDischarge(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "DISCHARGE_ORDER", ScopeType.WARD, currentWardId(id));
        Admission admission = orderDischargeService.order(new OrderDischargeCommand(id, actor.userId()));
        return AdmissionResponse.from(admission);
    }

    // §3.9: reason required — enforced in ReverseDischargeOrderService
    // (MissingReversalReasonException), not just @NotBlank here, since the
    // service is the one path other callers (a future UI shortcut, a
    // script) would also go through.
    @PostMapping("/{id}/discharge-order/reversal")
    public AdmissionResponse reverseDischargeOrder(
            @PathVariable UUID id, @Valid @RequestBody ReverseDischargeOrderRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "DISCHARGE_ORDER", ScopeType.WARD, currentWardId(id));
        Admission admission = reverseDischargeOrderService.reverse(
                new ReverseDischargeOrderCommand(id, request.reason(), actor.userId()));
        return AdmissionResponse.from(admission);
    }

    // §3.6/§3.7. THE INVARIANT lives in CompleteDischargeService, not
    // here: an outstanding balance never blocks this call.
    @PostMapping("/{id}/discharge")
    public AdmissionResponse completeDischarge(
            @PathVariable UUID id, @Valid @RequestBody CompleteDischargeRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "DISCHARGE_ORDER", ScopeType.WARD, currentWardId(id));
        Admission admission = completeDischargeService.complete(
                new CompleteDischargeCommand(id, request.actualDepartureAt(), actor.userId()));
        return AdmissionResponse.from(admission);
    }

    // 17-ward-nurse.md §9: ADMITTED/STABLE/CRITICAL only — DISCHARGED
    // belongs to the discharge flow above, TRANSFERRED to §8.3 ward
    // transfer (not in this item's scope). UpdateWardStatusService rejects
    // anything else with WardStatusNotSettableException.
    @PostMapping("/{id}/ward-status")
    public AdmissionResponse updateWardStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateWardStatusRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "WARD_STATUS_UPDATE", ScopeType.WARD, currentWardId(id));
        Admission admission = updateWardStatusService.update(
                new UpdateWardStatusCommand(id, request.wardStatus(), actor.userId()));
        return AdmissionResponse.from(admission);
    }

    private UUID currentWardId(UUID admissionId) {
        BedAssignment currentBed = bedAssignmentRepository.findActiveByAdmissionId(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission has no active bed assignment: " + admissionId));
        return bedRepository.findById(currentBed.bedId())
                .orElseThrow(() -> new NoSuchElementException("Bed not found: " + currentBed.bedId()))
                .wardId();
    }

    private void requirePermission(Actor actor, String permissionCode, ScopeType scopeType, UUID scopeId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(scopeType, scopeId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
