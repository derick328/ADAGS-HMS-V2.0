package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.MedicationAdministration;
import com.adags.modules.inpatient.domain.MedicationAdministrationNotDueException;
import com.adags.modules.inpatient.domain.MedicationAdministrationRepository;
import com.adags.modules.inpatient.domain.MedicationAdministrationStatus;
import com.adags.modules.inpatient.domain.MedicationWitnessRequiredException;
import com.adags.modules.inpatient.domain.OmissionReason;
import com.adags.modules.inpatient.domain.StaffDirectory;
import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
import com.adags.modules.inpatient.domain.WardAlertSourceType;
import com.adags.modules.pharmacy.PharmacyFacade;
import com.adags.shared.kernel.DispensedBatchSummary;
import com.adags.shared.kernel.PrescriptionLineMarSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 17-ward-nurse.md §5.1-§5.4: the compare-and-swap mark. Double-tap safety
// comes from MedicationAdministrationRepository.mark's WHERE status = 'DUE'
// — a second call for the same slot finds zero rows and this throws
// MedicationAdministrationNotDueException, not a second charted dose (root
// backend/CLAUDE.md "Concurrency").
@Service
public class MarkAdministrationService {

    private final MedicationAdministrationRepository medicationAdministrationRepository;
    private final AdmissionRepository admissionRepository;
    private final PharmacyFacade pharmacyFacade;
    private final WardAlertNotificationRepository wardAlertNotificationRepository;
    private final StaffDirectory staffDirectory;
    private final Clock clock;

    public MarkAdministrationService(
            MedicationAdministrationRepository medicationAdministrationRepository,
            AdmissionRepository admissionRepository,
            PharmacyFacade pharmacyFacade,
            WardAlertNotificationRepository wardAlertNotificationRepository,
            StaffDirectory staffDirectory,
            Clock clock
    ) {
        this.medicationAdministrationRepository = medicationAdministrationRepository;
        this.admissionRepository = admissionRepository;
        this.pharmacyFacade = pharmacyFacade;
        this.wardAlertNotificationRepository = wardAlertNotificationRepository;
        this.staffDirectory = staffDirectory;
        this.clock = clock;
    }

    @Transactional
    public MedicationAdministration mark(MarkAdministrationCommand command) {
        MedicationAdministration slot = medicationAdministrationRepository.findById(command.medicationAdministrationId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Medication administration slot not found: " + command.medicationAdministrationId()));

        PrescriptionLineMarSummary prescriptionSummary = pharmacyFacade.findPrescriptionLineForMar(slot.prescriptionLineId())
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + slot.prescriptionLineId()));

        UUID resolvedBatchId = null;
        if (command.status() == MedicationAdministrationStatus.GIVEN) {
            resolvedBatchId = resolveBatch(slot.prescriptionLineId(), command.batchId());
            requireWitnessIfNeeded(prescriptionSummary, command);
        } else if (isOmissionStatus(command.status()) && command.omissionReason() == null) {
            throw new IllegalArgumentException("omissionReason is required for " + command.status() + ".");
        }

        Instant now = Instant.now(clock);
        MedicationAdministration toMark = new MedicationAdministration(
                slot.id(), slot.admissionId(), slot.prescriptionLineId(), slot.scheduledAt(), command.status(),
                now, command.actorUserId(), command.doseGiven(), resolvedBatchId, command.witnessUserId(),
                command.omissionReason(), command.omissionNotes(), command.remarks(), slot.createdAt());

        MedicationAdministration marked = medicationAdministrationRepository.mark(toMark)
                .orElseThrow(() -> new MedicationAdministrationNotDueException(command.medicationAdministrationId()));

        if (command.omissionReason() == OmissionReason.DRUG_UNAVAILABLE) {
            notifyPharmacyOfStockout(marked, prescriptionSummary);
        }

        return marked;
    }

    private UUID resolveBatch(UUID prescriptionLineId, UUID requestedBatchId) {
        List<DispensedBatchSummary> dispensedBatches = pharmacyFacade.findDispensedBatches(prescriptionLineId);
        if (dispensedBatches.isEmpty()) {
            throw new IllegalStateException("No batch has been dispensed for this prescription line yet: " + prescriptionLineId);
        }
        if (requestedBatchId != null) {
            boolean valid = dispensedBatches.stream().anyMatch(b -> b.batchId().equals(requestedBatchId));
            if (!valid) {
                throw new IllegalArgumentException("Batch " + requestedBatchId + " was not dispensed for this line.");
            }
            return requestedBatchId;
        }
        if (dispensedBatches.size() == 1) {
            return dispensedBatches.get(0).batchId();
        }
        throw new IllegalArgumentException(
                "batchId is required — this line's dispensing spans " + dispensedBatches.size() + " batches.");
    }

    private void requireWitnessIfNeeded(PrescriptionLineMarSummary prescriptionSummary, MarkAdministrationCommand command) {
        if (!prescriptionSummary.requiresAdministrationWitness()) {
            return;
        }
        if (command.witnessUserId() == null || command.witnessUserId().equals(command.actorUserId())) {
            throw new MedicationWitnessRequiredException(prescriptionSummary.prescriptionLineId());
        }
    }

    private boolean isOmissionStatus(MedicationAdministrationStatus status) {
        return status == MedicationAdministrationStatus.REFUSED || status == MedicationAdministrationStatus.OMITTED
                || status == MedicationAdministrationStatus.HELD;
    }

    private void notifyPharmacyOfStockout(MedicationAdministration marked, PrescriptionLineMarSummary prescriptionSummary) {
        Admission admission = admissionRepository.findById(marked.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + marked.admissionId()));
        staffDirectory.findOneActivePharmacist(admission.facilityId()).ifPresent(pharmacistId ->
                wardAlertNotificationRepository.insert(WardAlertNotification.raise(
                        marked.admissionId(), WardAlertSourceType.DRUG_UNAVAILABLE, marked.id(),
                        "Ward stockout: " + prescriptionSummary.itemName() + " unavailable for a due dose.",
                        pharmacistId)));
    }
}
