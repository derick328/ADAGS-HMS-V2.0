package com.adags.modules.patients.api;

import com.adags.modules.patients.application.RegisterPatientCommand;
import com.adags.modules.patients.application.RegisterPatientService;
import com.adags.modules.patients.application.SearchPatientsService;
import com.adags.modules.patients.domain.InsurancePolicy;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.patients.domain.PatientAddress;
import com.adags.modules.patients.domain.PatientListPage;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.modules.patients.domain.PatientSearchResult;
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

// 05-receptionist.md §8. Emergency fast path (⚠4), PATCH (amend), visit
// history, and invoices are out of this slice — different specs, not this
// module's encounter-lifecycle work.
//
// register() is scoped to the facility the patient is being registered at.
// search() and getById() are deliberately NOT scoped: Patient carries no
// facility_id (root CLAUDE.md "Facility" — patients are shared across
// facilities, 21-multi-facility.md §3), so there is no scope value to check
// a role assignment's scope against. This is not the coarse shortcut —
// there is genuinely nothing here to scope.
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final RegisterPatientService registerPatientService;
    private final SearchPatientsService searchPatientsService;
    private final PatientRepository patientRepository;
    private final AuthorizationService authorizationService;

    public PatientController(
            RegisterPatientService registerPatientService,
            SearchPatientsService searchPatientsService,
            PatientRepository patientRepository,
            AuthorizationService authorizationService
    ) {
        this.registerPatientService = registerPatientService;
        this.searchPatientsService = searchPatientsService;
        this.patientRepository = patientRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse register(@Valid @RequestBody RegisterPatientRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PATIENT_CREATE", ScopeRef.of(ScopeType.FACILITY, request.facilityId()))) {
            throw new AccessDeniedException("Missing permission: PATIENT_CREATE");
        }

        RegisterPatientCommand command = new RegisterPatientCommand(
                request.givenName(), request.middleName(), request.familyName(), request.dateOfBirth(),
                request.ageYears(), request.ageMonths(), request.gender(), request.maritalStatus(),
                request.nationalId(), request.phone(), request.email(), request.occupation(),
                toNextOfKin(request.nextOfKin()), toAddresses(request.addresses()),
                toInsurancePolicy(request.insurancePolicy()), request.facilityId(), actor.userId(),
                request.notSamePersonConfirmationReason());

        Patient saved = registerPatientService.register(command);
        return PatientResponse.from(saved);
    }

    @GetMapping("/search")
    public List<PatientSearchResult> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal Actor actor
    ) {
        // Unscoped — see class comment: no facility_id to check a scope against.
        if (!authorizationService.hasPermission(actor, "PATIENT_SEARCH")) {
            throw new AccessDeniedException("Missing permission: PATIENT_SEARCH");
        }
        return searchPatientsService.search(query, limit, actor.userId());
    }

    // Admin Patients page and the doctor workspace's "All patients" tab open
    // on this — see PatientListResult for the deviation from
    // 05-receptionist.md §5.1's "no list-all" line. Safeguards: bounded page
    // size (SearchPatientsService.MAX_PAGE_SIZE), one audit row per page
    // fetched, gated on PATIENT_SEARCH.
    @GetMapping
    public PatientListPage listRecent(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "payer", required = false) String payer,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
            @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "PATIENT_SEARCH")) {
            throw new AccessDeniedException("Missing permission: PATIENT_SEARCH");
        }
        return searchPatientsService.listRecent(from, to, payer, page, pageSize, actor.userId());
    }

    // 05-receptionist.md §3's "Registered today" dashboard tile — a count,
    // not a list. 18-admin.md's "no browse-all-patients" reasoning applies
    // here too: this deliberately returns a number, never rows.
    @GetMapping("/registered-count")
    public long registeredCount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "PATIENT_SEARCH")) {
            throw new AccessDeniedException("Missing permission: PATIENT_SEARCH");
        }
        return patientRepository.countCreatedBetween(from, to);
    }

    @GetMapping("/{id}")
    public PatientResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        // Unscoped — see class comment: no facility_id to check a scope against.
        if (!authorizationService.hasPermission(actor, "PATIENT_READ_DEMOGRAPHIC")) {
            throw new AccessDeniedException("Missing permission: PATIENT_READ_DEMOGRAPHIC");
        }
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Patient not found: " + id));
        return PatientResponse.from(patient);
    }

    private NextOfKin toNextOfKin(NextOfKinRequest request) {
        String other = request.relationshipOther() == null ? null : request.relationshipOther().trim();
        if (other != null && other.isEmpty()) {
            other = null;
        }
        return new NextOfKin(null, null, request.fullName(), request.relationship(),
                other, request.phone(), request.isGuardian());
    }

    private List<PatientAddress> toAddresses(List<PatientAddressRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(request -> new PatientAddress(null, null, request.addressType(), request.region(),
                        request.district(), request.ward(), request.streetOrVillage(), request.landmark()))
                .toList();
    }

    private InsurancePolicy toInsurancePolicy(InsurancePolicyRequest request) {
        if (request == null) {
            return null;
        }
        return new InsurancePolicy(null, null, request.schemeId(), request.policyNumber(), request.memberNumber(),
                request.isPrincipal(), request.principalName(), request.validFrom(), request.validTo(),
                "UNVERIFIED", null);
    }
}
