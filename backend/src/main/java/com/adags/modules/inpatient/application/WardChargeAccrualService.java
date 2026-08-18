package com.adags.modules.inpatient.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.BedDayCalculator;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.SystemActorPort;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardChargeAccrual;
import com.adags.modules.inpatient.domain.WardChargeAccrualRepository;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 04-procedure-bedday-rules.md §3.5: the nightly accrual job's atomic
// per-admission unit — one WardChargeAccrual row per occupied bed-day,
// posted through billing's ChargePostingService. Catch-up capable:
// candidates are every 10:00 boundary strictly after the last row this
// admission has (or its effective_start, on the very first run) and not
// after now, so a missed run posts every boundary it skipped rather than
// only the latest one.
//
// §3.4: "for each accrual boundary, take the set of BedAssignment rows
// overlapping that bed-day and charge the highest ward rate among them" —
// evaluated per boundary from this admission's full BedAssignment history,
// not just its current one, so a same-day transfer resolves correctly.
//
// A boundary with no overlapping BedAssignment at all (bed not yet
// assigned) stops the sweep for this admission rather than skipping ahead
// over it — the gap gets caught up once a bed exists, on a later run,
// instead of a night with no assigned bed silently going unaccounted for
// while later nights post around it.
@Service
public class WardChargeAccrualService {

    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");
    private static final String ACCRUAL_EVENT_TYPE = "WardChargeAccrual";
    private static final String WARD_BED_DAY_GENERAL = "WARD_BED_DAY_GENERAL";
    private static final String WARD_BED_DAY_PRIVATE = "WARD_BED_DAY_PRIVATE";

    private final AdmissionRepository admissionRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final BedRepository bedRepository;
    private final WardRepository wardRepository;
    private final WardChargeAccrualRepository wardChargeAccrualRepository;
    private final EncounterFacade encounterFacade;
    private final BillingFacade billingFacade;
    private final SystemActorPort systemActorPort;
    private final Clock clock;

    public WardChargeAccrualService(
            AdmissionRepository admissionRepository,
            BedAssignmentRepository bedAssignmentRepository,
            BedRepository bedRepository,
            WardRepository wardRepository,
            WardChargeAccrualRepository wardChargeAccrualRepository,
            EncounterFacade encounterFacade,
            BillingFacade billingFacade,
            SystemActorPort systemActorPort,
            Clock clock
    ) {
        this.admissionRepository = admissionRepository;
        this.bedAssignmentRepository = bedAssignmentRepository;
        this.bedRepository = bedRepository;
        this.wardRepository = wardRepository;
        this.wardChargeAccrualRepository = wardChargeAccrualRepository;
        this.encounterFacade = encounterFacade;
        this.billingFacade = billingFacade;
        this.systemActorPort = systemActorPort;
        this.clock = clock;
    }

    /** Returns the number of bed-day rows posted for this admission in this run. */
    @Transactional
    public int accrueForAdmission(UUID admissionId) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + admissionId));
        List<BedAssignment> assignments = bedAssignmentRepository.findByAdmissionId(admissionId);
        if (assignments.isEmpty()) {
            return 0;
        }

        Instant effectiveStart = BedDayCalculator.effectiveStart(admission.admittedAt(), FACILITY_ZONE);
        Instant windowStart = wardChargeAccrualRepository.findLastPostedBoundary(admissionId).orElse(effectiveStart);
        List<Instant> candidates = BedDayCalculator.boundariesToPost(windowStart, Instant.now(clock), FACILITY_ZONE);
        if (candidates.isEmpty()) {
            return 0;
        }

        EncounterContext context = encounterFacade.findContext(admission.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + admission.encounterId()));

        int posted = 0;
        Instant currentWindowStart = windowStart;
        for (Instant boundary : candidates) {
            Optional<RatedAssignment> winner = highestRateOverlap(assignments, currentWindowStart, boundary);
            if (winner.isEmpty()) {
                break;
            }
            postOneNight(admission, context, winner.get(), boundary);
            currentWindowStart = boundary;
            posted++;
        }
        return posted;
    }

    /**
     * 04-procedure-bedday-rules.md §3.6/§3.7: called once at discharge
     * completion with the gap-adjusted billing_stop. The nightly job
     * tracks bed occupancy only — it has no notion of a pending discharge
     * order — so it may already have posted a boundary the final gap rule
     * ends up forgiving. Those are reversed here (voided, never deleted);
     * whatever the §3.1 day count still requires is then posted, including
     * the final partial day that never reaches its own boundary — the
     * same "+1" that formula adds — keyed at billing_stop itself since
     * there is no real 10:00 crossing to key it at.
     */
    @Transactional
    public void reconcileForDischarge(UUID admissionId, Instant billingStop, UUID actorUserId) {
        Admission admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + admissionId));
        List<WardChargeAccrual> existing = wardChargeAccrualRepository.findByAdmissionId(admissionId);
        int totalDays = BedDayCalculator.daysFor(admission.admittedAt(), billingStop, FACILITY_ZONE);

        if (totalDays == 0) {
            // Under the 4-hour floor overall — every accrued row (even one
            // keyed before billing_stop, e.g. a boundary crossed minutes
            // into a short stay) is forgiven.
            for (WardChargeAccrual row : existing) {
                billingFacade.voidUnpaidServiceCharge(row.invoiceLineId(),
                        "Reversed at discharge — stay was under the 4-hour floor.", actorUserId);
            }
            return;
        }

        for (WardChargeAccrual row : existing) {
            if (row.accrualBoundaryAt().isAfter(billingStop)) {
                billingFacade.voidUnpaidServiceCharge(row.invoiceLineId(),
                        "Reversed at discharge — falls within the 4-hour departure grace.", actorUserId);
            }
        }

        List<BedAssignment> assignments = bedAssignmentRepository.findByAdmissionId(admissionId);
        Instant effectiveStart = BedDayCalculator.effectiveStart(admission.admittedAt(), FACILITY_ZONE);
        List<Instant> realBoundaries = BedDayCalculator.boundariesBetween(effectiveStart, billingStop, FACILITY_ZONE);
        Set<Instant> validKeys = existing.stream()
                .map(WardChargeAccrual::accrualBoundaryAt)
                .filter(boundary -> !boundary.isAfter(billingStop))
                .collect(Collectors.toSet());

        EncounterContext context = encounterFacade.findContext(admission.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + admission.encounterId()));

        Instant currentWindowStart = effectiveStart;
        for (Instant boundary : realBoundaries) {
            if (!validKeys.contains(boundary)) {
                highestRateOverlap(assignments, currentWindowStart, boundary)
                        .ifPresent(winner -> postOneNight(admission, context, winner, boundary));
            }
            currentWindowStart = boundary;
        }
        if (!validKeys.contains(billingStop)) {
            highestRateOverlap(assignments, currentWindowStart, billingStop)
                    .ifPresent(winner -> postOneNight(admission, context, winner, billingStop));
        }
    }

    private void postOneNight(Admission admission, EncounterContext context, RatedAssignment winner, Instant boundary) {
        WardChargeAccrual reserved = wardChargeAccrualRepository.reserve(new WardChargeAccrual(
                null, admission.id(), winner.bedAssignmentId(), winner.wardId(), boundary, winner.dailyRate(), null, null));

        String serviceItemCode = winner.wardClass() == WardClass.PRIVATE ? WARD_BED_DAY_PRIVATE : WARD_BED_DAY_GENERAL;
        UUID invoiceLineId = billingFacade.postFixedAmountCharge(admission.encounterId(), context.patientId(),
                context.facilityId(), context.payerType(), serviceItemCode, winner.dailyRate(),
                ACCRUAL_EVENT_TYPE, reserved.id(), systemActorPort.systemActorId());

        wardChargeAccrualRepository.attachInvoiceLine(reserved.id(), invoiceLineId);
    }

    private Optional<RatedAssignment> highestRateOverlap(List<BedAssignment> assignments, Instant windowStart, Instant windowEnd) {
        RatedAssignment winner = null;
        for (BedAssignment assignment : assignments) {
            if (!overlaps(assignment, windowStart, windowEnd)) {
                continue;
            }
            Bed bed = bedRepository.findById(assignment.bedId())
                    .orElseThrow(() -> new NoSuchElementException("Bed not found: " + assignment.bedId()));
            Ward ward = wardRepository.findById(bed.wardId())
                    .orElseThrow(() -> new NoSuchElementException("Ward not found: " + bed.wardId()));
            RatedAssignment candidate = new RatedAssignment(assignment.id(), ward.id(), ward.wardClass(), ward.dailyRate());
            if (winner == null || candidate.dailyRate.isGreaterThan(winner.dailyRate)) {
                winner = candidate;
            }
        }
        return Optional.ofNullable(winner);
    }

    private boolean overlaps(BedAssignment assignment, Instant windowStart, Instant windowEnd) {
        boolean startsBeforeWindowEnds = assignment.assignedAt().isBefore(windowEnd);
        boolean endsAfterWindowStarts = assignment.releasedAt() == null || assignment.releasedAt().isAfter(windowStart);
        return startsBeforeWindowEnds && endsAfterWindowStarts;
    }

    private record RatedAssignment(UUID bedAssignmentId, UUID wardId, WardClass wardClass, Money dailyRate) {
    }
}
