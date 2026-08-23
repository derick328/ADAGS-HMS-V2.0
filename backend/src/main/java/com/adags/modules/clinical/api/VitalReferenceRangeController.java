package com.adags.modules.clinical.api;

import com.adags.modules.clinical.domain.AgeBand;
import com.adags.modules.clinical.domain.Sex;
import com.adags.modules.clinical.domain.VitalReferenceRangeRepository;
import com.adags.modules.clinical.domain.VitalType;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.kernel.PatientDemographics;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 06-triage.md §2.2: "Each field shows implausible/abnormal bounds from
// VitalReferenceRange" — the read side the vitals form needs but nothing
// exposed before now (RecordVitalSignsService only ever *evaluated*
// against this table server-side; showing the bounds to a nurse before
// she types a number is a different, previously-missing need). Age
// band/sex resolve the same way RecordVitalSignsService already does
// (AgeBand.forAge / Sex.fromPatientGender) — one source of truth for that
// boundary logic, not duplicated in TypeScript.
@RestController
public class VitalReferenceRangeController {

    private static final List<VitalType> ALL_VITAL_TYPES = List.of(
            VitalType.TEMPERATURE, VitalType.SYSTOLIC_BP, VitalType.DIASTOLIC_BP,
            VitalType.PULSE, VitalType.RESPIRATORY_RATE, VitalType.SPO2);

    private final VitalReferenceRangeRepository rangeRepository;
    private final PatientFacade patientFacade;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public VitalReferenceRangeController(
            VitalReferenceRangeRepository rangeRepository,
            PatientFacade patientFacade,
            AuthorizationService authorizationService,
            Clock clock
    ) {
        this.rangeRepository = rangeRepository;
        this.patientFacade = patientFacade;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    // Vitals not configured for this patient's age band/sex are omitted,
    // not zero-filled — the form treats a missing entry as "no bounds
    // known," never as "anything goes." (VitalReferenceRangeNotConfiguredException
    // is what actually recording a vital against an unconfigured band throws.)
    @GetMapping("/api/clinical/vital-reference-ranges")
    public List<VitalReferenceRangeResponse> forPatient(@RequestParam UUID patientId, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "VITALS_CREATE")) {
            throw new AccessDeniedException("Missing permission: VITALS_CREATE");
        }
        PatientDemographics demographics = patientFacade.findDemographics(patientId)
                .orElseThrow(() -> new NoSuchElementException("Patient not found: " + patientId));
        LocalDate asOf = LocalDate.now(clock);
        AgeBand ageBand = AgeBand.forAge(demographics.dateOfBirth(), asOf);
        Sex sex = Sex.fromPatientGender(demographics.gender());

        return ALL_VITAL_TYPES.stream()
                .flatMap(vitalType -> rangeRepository.findCurrent(vitalType, ageBand, sex).stream())
                .map(VitalReferenceRangeResponse::from)
                .toList();
    }
}
