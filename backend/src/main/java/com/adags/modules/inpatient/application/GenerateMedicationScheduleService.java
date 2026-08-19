package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.MedicationAdministration;
import com.adags.modules.inpatient.domain.MedicationAdministrationRepository;
import com.adags.modules.inpatient.domain.MedicationFrequency;
import com.adags.modules.inpatient.domain.MedicationScheduleGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// 17-ward-nurse.md §5.1: called from PlacePrescriptionService (pharmacy),
// same transaction, the moment a ward prescription is placed. Idempotent —
// generating twice for the same line is a no-op past the first call, via
// the partial unique index insertSchedule's ON CONFLICT relies on.
@Service
public class GenerateMedicationScheduleService {

    private final MedicationAdministrationRepository medicationAdministrationRepository;

    public GenerateMedicationScheduleService(MedicationAdministrationRepository medicationAdministrationRepository) {
        this.medicationAdministrationRepository = medicationAdministrationRepository;
    }

    @Transactional
    public void generate(GenerateMedicationScheduleCommand command) {
        MedicationFrequency frequency = MedicationFrequency.valueOf(command.frequency());
        List<Instant> scheduledTimes = MedicationScheduleGenerator.generate(
                frequency, command.durationDays(), command.startAt());
        List<MedicationAdministration> slots = scheduledTimes.stream()
                .map(scheduledAt -> MedicationAdministration.dueSlot(
                        command.admissionId(), command.prescriptionLineId(), scheduledAt))
                .toList();
        medicationAdministrationRepository.insertSchedule(slots);
    }
}
