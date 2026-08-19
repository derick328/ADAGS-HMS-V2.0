package com.adags.modules.inpatient.application;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
import com.adags.modules.inpatient.domain.WardAlertSourceType;
import com.adags.shared.kernel.AbnormalVitalReading;
import com.adags.shared.kernel.WardVitalsResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

// 17-ward-nurse.md §4.1/§4.3: ward rounds vitals, reusing ClinicalFacade's
// VitalSigns/VitalReferenceRange machinery (context = WARD_ROUNDS) rather
// than a second table or a second evaluator. §4.3: "an alert must reach
// someone" — a breach here raises a WardAlertNotification addressed to the
// admitting doctor (the same simplification laboratory's
// CriticalResultNotification.raise makes: notify the doctor of record, not
// a "covering doctor roster" that doesn't exist) and suggests CRITICAL —
// suggests, not sets; UpdateWardStatusService is the only thing that
// actually changes ward_status (§9).
@Service
public class RecordWardVitalsService {

    private final ClinicalFacade clinicalFacade;
    private final AdmissionRepository admissionRepository;
    private final WardAlertNotificationRepository wardAlertNotificationRepository;
    private final Clock clock;

    public RecordWardVitalsService(
            ClinicalFacade clinicalFacade,
            AdmissionRepository admissionRepository,
            WardAlertNotificationRepository wardAlertNotificationRepository,
            Clock clock
    ) {
        this.clinicalFacade = clinicalFacade;
        this.admissionRepository = admissionRepository;
        this.wardAlertNotificationRepository = wardAlertNotificationRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID record(RecordWardVitalsCommand command) {
        Admission admission = admissionRepository.findById(command.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + command.admissionId()));

        WardVitalsResult result = clinicalFacade.recordWardVitalSigns(
                admission.encounterId(), admission.patientId(), admission.id(),
                command.temperatureC(), command.systolicMmHg(), command.diastolicMmHg(),
                command.pulseBpm(), command.respiratoryRate(), command.spo2Pct(),
                command.weightKg(), command.heightCm(), command.avpu(), command.bloodGlucoseMmol(),
                command.painScore(), command.intakeMl(), command.outputMl(),
                command.recordedBy(), command.occurredAt());

        if (result.hasAbnormalReading()) {
            wardAlertNotificationRepository.insert(WardAlertNotification.raise(
                    admission.id(), WardAlertSourceType.VITAL_BREACH, result.vitalSignsId(),
                    describeBreach(result), admission.admittingDoctorId()));
            admissionRepository.suggestCriticalStatus(admission.id(), Instant.now(clock));
        }

        return result.vitalSignsId();
    }

    private String describeBreach(WardVitalsResult result) {
        return "Breaching vitals: " + result.abnormalReadings().stream()
                .map(AbnormalVitalReading::vitalType)
                .collect(Collectors.joining(", "));
    }
}
