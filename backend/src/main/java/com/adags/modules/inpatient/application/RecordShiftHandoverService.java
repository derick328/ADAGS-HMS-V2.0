package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.MedicationAdministration;
import com.adags.modules.inpatient.domain.MedicationAdministrationRepository;
import com.adags.modules.inpatient.domain.ShiftAssignment;
import com.adags.modules.inpatient.domain.ShiftAssignmentRepository;
import com.adags.modules.inpatient.domain.ShiftHandover;
import com.adags.modules.inpatient.domain.ShiftHandoverRepository;
import com.adags.modules.inpatient.domain.ShiftRoleContext;
import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 17-ward-nurse.md §7: "PRE-POPULATE from what the system already knows...
// that pre-population is most of the value — a nurse writing from memory
// at the end of a twelve-hour shift misses things the system has."
//
// Pre-populated here from what's reachable through this module's own
// repositories: unacknowledged ward alerts (vitals breaches, wound
// infections) and overdue MAR slots. Pending lab results and "orders added
// this shift" are a documented gap, not a silent omission — laboratory has
// no facade yet (this item already spans five sub-items; building a fourth
// new facade for read-only pre-population is left for a follow-up pass).
//
// §4.2: "The roster is what identifies the incoming nurse." Resolved from
// ShiftAssignment for this ward on today's facility-local date — the first
// WARD_NURSE assignment that isn't the outgoing nurse herself.
@Service
public class RecordShiftHandoverService {

    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private final ShiftHandoverRepository shiftHandoverRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WardAlertNotificationRepository wardAlertNotificationRepository;
    private final MedicationAdministrationRepository medicationAdministrationRepository;
    private final Clock clock;

    public RecordShiftHandoverService(
            ShiftHandoverRepository shiftHandoverRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            WardAlertNotificationRepository wardAlertNotificationRepository,
            MedicationAdministrationRepository medicationAdministrationRepository,
            Clock clock
    ) {
        this.shiftHandoverRepository = shiftHandoverRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.wardAlertNotificationRepository = wardAlertNotificationRepository;
        this.medicationAdministrationRepository = medicationAdministrationRepository;
        this.clock = clock;
    }

    @Transactional
    public ShiftHandover record(RecordShiftHandoverCommand command) {
        List<String> tasks = new ArrayList<>(prePopulateOutstandingTasks(command.admissionId()));
        if (command.additionalOutstandingTasks() != null) {
            tasks.addAll(command.additionalOutstandingTasks());
        }

        UUID incomingNurseId = resolveIncomingNurse(command.wardId(), command.outgoingNurseId());

        ShiftHandover handover = ShiftHandover.record(command.admissionId(), command.wardId(), command.shiftFrom(),
                command.shiftTo(), command.outgoingNurseId(), incomingNurseId, command.situation(),
                command.background(), command.assessment(), command.recommendation(), tasks);
        return shiftHandoverRepository.insert(handover);
    }

    private List<String> prePopulateOutstandingTasks(UUID admissionId) {
        List<String> tasks = new ArrayList<>();

        List<WardAlertNotification> alerts = wardAlertNotificationRepository.findUnacknowledgedByAdmissionId(admissionId);
        tasks.addAll(alerts.stream()
                .map(alert -> "Unacknowledged alert (" + alert.sourceType() + "): " + alert.detail())
                .toList());

        List<MedicationAdministration> overdueMar = medicationAdministrationRepository
                .findOverdueByAdmissionId(admissionId, Instant.now(clock));
        tasks.addAll(overdueMar.stream()
                .map(slot -> "Overdue MAR slot due " + slot.scheduledAt() + " (prescription line " + slot.prescriptionLineId() + ")")
                .toList());

        return tasks;
    }

    private UUID resolveIncomingNurse(UUID wardId, UUID outgoingNurseId) {
        List<ShiftAssignment> today = shiftAssignmentRepository.findByWardIdAndShiftDate(
                wardId, Instant.now(clock).atZone(FACILITY_ZONE).toLocalDate());
        Optional<ShiftAssignment> incoming = today.stream()
                .filter(a -> a.roleContext() == ShiftRoleContext.WARD_NURSE)
                .filter(a -> !a.userId().equals(outgoingNurseId))
                .findFirst();
        return incoming.map(ShiftAssignment::userId).orElse(null);
    }
}
