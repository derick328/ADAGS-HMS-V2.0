package com.adags.modules.patients.infrastructure;

import com.adags.modules.patients.PatientFacade;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.shared.kernel.PatientDemographics;
import com.adags.shared.kernel.PatientIdentity;
import com.adags.shared.kernel.PayerType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Component
public class PatientFacadeImpl implements PatientFacade {

    private final PatientRepository patientRepository;

    public PatientFacadeImpl(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Override
    public boolean exists(UUID patientId) {
        return patientRepository.findById(patientId).isPresent();
    }

    @Override
    public PayerType resolvePayerType(UUID patientId, LocalDate onDate) {
        boolean hasActivePolicy = patientRepository.findInsurancePoliciesByPatientId(patientId).stream()
                .anyMatch(policy -> policy.isValidOn(onDate));
        return hasActivePolicy ? PayerType.INSURANCE : PayerType.CASH;
    }

    @Override
    public Optional<PatientDemographics> findDemographics(UUID patientId) {
        return patientRepository.findById(patientId)
                .map(patient -> new PatientDemographics(patient.dateOfBirth(), patient.gender()));
    }

    @Override
    public Optional<PatientIdentity> findIdentity(UUID patientId) {
        return patientRepository.findById(patientId)
                .map(patient -> new PatientIdentity(
                        patient.hospitalNo(), patient.givenName(), patient.middleName(), patient.familyName()));
    }
}
