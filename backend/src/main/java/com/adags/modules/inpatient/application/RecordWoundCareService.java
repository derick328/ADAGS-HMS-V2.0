package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
import com.adags.modules.inpatient.domain.WardAlertSourceType;
import com.adags.modules.inpatient.domain.WoundCare;
import com.adags.modules.inpatient.domain.WoundCareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 17-ward-nurse.md §6: "signsOfInfection = true should notify the covering
// doctor. A wound infection noticed by a nurse at 22:00 that waits for the
// morning round is a preventable escalation." Addressed to
// admission.admittingDoctorId — same simplification RecordWardVitalsService
// and the lab module both make (no "covering doctor roster" exists).
@Service
public class RecordWoundCareService {

    private final WoundCareRepository woundCareRepository;
    private final AdmissionRepository admissionRepository;
    private final WardAlertNotificationRepository wardAlertNotificationRepository;

    public RecordWoundCareService(
            WoundCareRepository woundCareRepository,
            AdmissionRepository admissionRepository,
            WardAlertNotificationRepository wardAlertNotificationRepository
    ) {
        this.woundCareRepository = woundCareRepository;
        this.admissionRepository = admissionRepository;
        this.wardAlertNotificationRepository = wardAlertNotificationRepository;
    }

    @Transactional
    public WoundCare record(RecordWoundCareCommand command) {
        Admission admission = admissionRepository.findById(command.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + command.admissionId()));

        WoundCare saved = woundCareRepository.insert(WoundCare.record(
                command.admissionId(), command.appearance(), command.dressingChanged(), command.dressingType(),
                command.signsOfInfection(), command.infectionDescription(), command.remarks(), command.recordedBy()));

        if (saved.signsOfInfection()) {
            wardAlertNotificationRepository.insert(WardAlertNotification.raise(
                    admission.id(), WardAlertSourceType.WOUND_INFECTION, saved.id(),
                    "Signs of wound infection: " + saved.infectionDescription(), admission.admittingDoctorId()));
        }

        return saved;
    }
}
