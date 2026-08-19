package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.InpatientFacade;
import com.adags.modules.inpatient.application.GenerateMedicationScheduleCommand;
import com.adags.modules.inpatient.application.GenerateMedicationScheduleService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class InpatientFacadeImpl implements InpatientFacade {

    private final AdmissionRepository admissionRepository;
    private final GenerateMedicationScheduleService generateMedicationScheduleService;

    public InpatientFacadeImpl(
            AdmissionRepository admissionRepository,
            GenerateMedicationScheduleService generateMedicationScheduleService
    ) {
        this.admissionRepository = admissionRepository;
        this.generateMedicationScheduleService = generateMedicationScheduleService;
    }

    @Override
    public Optional<UUID> findActiveAdmissionId(UUID encounterId) {
        return admissionRepository.findByEncounterId(encounterId)
                .filter(admission -> admission.actualDepartureAt() == null)
                .map(Admission::id);
    }

    @Override
    public void generateMedicationAdministrationSchedule(
            UUID admissionId, UUID prescriptionLineId, String frequency, int durationDays, Instant startAt
    ) {
        generateMedicationScheduleService.generate(new GenerateMedicationScheduleCommand(
                admissionId, prescriptionLineId, frequency, durationDays, startAt));
    }
}
