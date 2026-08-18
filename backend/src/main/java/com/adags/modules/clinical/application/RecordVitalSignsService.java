package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.AgeBand;
import com.adags.modules.clinical.domain.RecordedVital;
import com.adags.modules.clinical.domain.Sex;
import com.adags.modules.clinical.domain.VitalEvaluation;
import com.adags.modules.clinical.domain.VitalReferenceRange;
import com.adags.modules.clinical.domain.VitalReferenceRangeEvaluator;
import com.adags.modules.clinical.domain.VitalReferenceRangeNotConfiguredException;
import com.adags.modules.clinical.domain.VitalReferenceRangeRepository;
import com.adags.modules.clinical.domain.VitalSigns;
import com.adags.modules.clinical.domain.VitalSignsRepository;
import com.adags.modules.clinical.domain.VitalType;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.kernel.PatientDemographics;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

// 06-triage.md §2.2, item 2 of the clinical-core build order: "each vital is
// checked against the patient's age band via the evaluator from item 1.
// Implausible rejects with the bound named. Abnormal saves with a flag, and
// the applied range id is snapshotted." This is that check, wired to real
// patient demographics and a real reference-range lookup — item 1
// (VitalReferenceRangeEvaluator) is pure and knows nothing about either.
@Service
public class RecordVitalSignsService {

    // root CLAUDE.md "Time": store UTC, render/derive facility-local. Age in
    // years is a facility-local-date calculation, not a raw UTC one — a
    // patient measured at 01:00 UTC on their birthday must not be evaluated
    // a day early or late because of the offset.
    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private final PatientFacade patientFacade;
    private final VitalReferenceRangeRepository vitalReferenceRangeRepository;
    private final VitalSignsRepository vitalSignsRepository;

    public RecordVitalSignsService(
            PatientFacade patientFacade,
            VitalReferenceRangeRepository vitalReferenceRangeRepository,
            VitalSignsRepository vitalSignsRepository
    ) {
        this.patientFacade = patientFacade;
        this.vitalReferenceRangeRepository = vitalReferenceRangeRepository;
        this.vitalSignsRepository = vitalSignsRepository;
    }

    public VitalSigns record(RecordVitalSignsCommand command) {
        PatientDemographics demographics = patientFacade.findDemographics(command.patientId())
                .orElseThrow(() -> new NoSuchElementException("Patient not found: " + command.patientId()));

        LocalDate asOf = command.occurredAt().atZone(FACILITY_ZONE).toLocalDate();
        AgeBand ageBand = AgeBand.forAge(demographics.dateOfBirth(), asOf);
        Sex sex = Sex.fromPatientGender(demographics.gender());

        List<RecordedVital> readings = new ArrayList<>();
        readings.add(evaluate(VitalType.TEMPERATURE, command.temperatureC(), ageBand, sex));
        readings.add(evaluate(VitalType.SYSTOLIC_BP, command.systolicMmHg(), ageBand, sex));
        readings.add(evaluate(VitalType.DIASTOLIC_BP, command.diastolicMmHg(), ageBand, sex));
        readings.add(evaluate(VitalType.PULSE, command.pulseBpm(), ageBand, sex));
        readings.add(evaluate(VitalType.RESPIRATORY_RATE, command.respiratoryRate(), ageBand, sex));
        readings.add(evaluate(VitalType.SPO2, command.spo2Pct(), ageBand, sex));

        VitalSigns toInsert = new VitalSigns(
                null,
                command.encounterId(),
                command.context(),
                command.contextRefId(),
                readings,
                command.weightKg(),
                command.heightCm(),
                null,
                command.avpu(),
                command.bloodGlucoseMmol(),
                command.recordedBy(),
                null,
                command.occurredAt());

        return vitalSignsRepository.insert(toInsert);
    }

    private RecordedVital evaluate(VitalType vitalType, double value, AgeBand ageBand, Sex sex) {
        VitalReferenceRange range = vitalReferenceRangeRepository.findCurrent(vitalType, ageBand, sex)
                .orElseThrow(() -> new VitalReferenceRangeNotConfiguredException(vitalType, ageBand));
        VitalEvaluation evaluation = VitalReferenceRangeEvaluator.evaluate(vitalType, value, range);
        return new RecordedVital(vitalType, evaluation.value(), evaluation.abnormal(), range.id());
    }
}
