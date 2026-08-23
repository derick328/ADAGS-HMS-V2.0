package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.InpatientFacade;
import com.adags.modules.inpatient.application.GenerateMedicationScheduleCommand;
import com.adags.modules.inpatient.application.GenerateMedicationScheduleService;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class InpatientFacadeImpl implements InpatientFacade {

    private final AdmissionRepository admissionRepository;
    private final GenerateMedicationScheduleService generateMedicationScheduleService;
    private final Clock clock;

    public InpatientFacadeImpl(
            AdmissionRepository admissionRepository,
            GenerateMedicationScheduleService generateMedicationScheduleService,
            Clock clock
    ) {
        this.admissionRepository = admissionRepository;
        this.generateMedicationScheduleService = generateMedicationScheduleService;
        this.clock = clock;
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

    @Override
    @Transactional
    public UUID admitFromProcedure(UUID encounterId, UUID patientId, UUID facilityId, UUID admittingDoctorId,
                                    String orderedWardClass, UUID actorUserId) {
        Admission toInsert = new Admission(null, encounterId, patientId, facilityId, admittingDoctorId,
                WardClass.valueOf(orderedWardClass), Instant.now(clock), null, null, null, null,
                WardStatus.ADMITTED, null, null, null, 0, null);
        return admissionRepository.insert(toInsert).id();
    }
}
