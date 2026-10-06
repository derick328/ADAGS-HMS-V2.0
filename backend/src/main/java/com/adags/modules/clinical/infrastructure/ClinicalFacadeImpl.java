package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.clinical.application.FinalizeConsultationRecordService;
import com.adags.modules.clinical.application.OpenConsultationRecordService;
import com.adags.modules.clinical.application.RecordVitalSignsCommand;
import com.adags.modules.clinical.application.RecordVitalSignsService;
import com.adags.modules.clinical.domain.AllergyRecord;
import com.adags.modules.clinical.domain.AllergyRecordRepository;
import com.adags.modules.clinical.domain.AllergySource;
import com.adags.modules.clinical.domain.Avpu;
import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.VitalSigns;
import com.adags.modules.clinical.domain.VitalSignsContext;
import com.adags.modules.clinical.domain.VitalSignsRepository;
import com.adags.modules.clinical.domain.RecordedVital;
import com.adags.shared.kernel.AbnormalVitalReading;
import com.adags.shared.kernel.AllergySummary;
import com.adags.shared.kernel.WardVitalsResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ClinicalFacadeImpl implements ClinicalFacade {

    private final RecordVitalSignsService recordVitalSignsService;
    private final AllergyRecordRepository allergyRecordRepository;
    private final OpenConsultationRecordService openConsultationRecordService;
    private final FinalizeConsultationRecordService finalizeConsultationRecordService;
    private final VitalSignsRepository vitalSignsRepository;

    public ClinicalFacadeImpl(
            RecordVitalSignsService recordVitalSignsService,
            AllergyRecordRepository allergyRecordRepository,
            OpenConsultationRecordService openConsultationRecordService,
            FinalizeConsultationRecordService finalizeConsultationRecordService,
            VitalSignsRepository vitalSignsRepository
    ) {
        this.recordVitalSignsService = recordVitalSignsService;
        this.allergyRecordRepository = allergyRecordRepository;
        this.openConsultationRecordService = openConsultationRecordService;
        this.finalizeConsultationRecordService = finalizeConsultationRecordService;
        this.vitalSignsRepository = vitalSignsRepository;
    }

    @Override
    public UUID recordTriageVitalSigns(
            UUID encounterId, UUID patientId, UUID triageAssessmentId,
            double temperatureC, double systolicMmHg, double diastolicMmHg,
            double pulseBpm, double respiratoryRate, double spo2Pct,
            double weightKg, double heightCm,
            String avpu, Double bloodGlucoseMmol,
            UUID recordedBy, Instant occurredAt
    ) {
        RecordVitalSignsCommand command = new RecordVitalSignsCommand(
                encounterId, patientId, VitalSignsContext.TRIAGE, triageAssessmentId,
                temperatureC, systolicMmHg, diastolicMmHg, pulseBpm, respiratoryRate, spo2Pct,
                weightKg, heightCm, avpu == null ? null : Avpu.valueOf(avpu), bloodGlucoseMmol,
                null, null, null, recordedBy, occurredAt);
        VitalSigns saved = recordVitalSignsService.record(command);
        return saved.id();
    }

    @Override
    public WardVitalsResult recordWardVitalSigns(
            UUID encounterId, UUID patientId, UUID admissionId,
            double temperatureC, double systolicMmHg, double diastolicMmHg,
            double pulseBpm, double respiratoryRate, double spo2Pct,
            double weightKg, double heightCm,
            String avpu, Double bloodGlucoseMmol, Integer painScore, Integer intakeMl, Integer outputMl,
            UUID recordedBy, Instant occurredAt
    ) {
        RecordVitalSignsCommand command = new RecordVitalSignsCommand(
                encounterId, patientId, VitalSignsContext.WARD_ROUNDS, admissionId,
                temperatureC, systolicMmHg, diastolicMmHg, pulseBpm, respiratoryRate, spo2Pct,
                weightKg, heightCm, avpu == null ? null : Avpu.valueOf(avpu), bloodGlucoseMmol,
                painScore, intakeMl, outputMl, recordedBy, occurredAt);
        VitalSigns saved = recordVitalSignsService.record(command);
        List<AbnormalVitalReading> abnormal = saved.readings().stream()
                .filter(RecordedVital::abnormal)
                .map(reading -> new AbnormalVitalReading(reading.vitalType().name(), reading.value()))
                .toList();
        return new WardVitalsResult(saved.id(), abnormal);
    }

    @Override
    public UUID recordPostOpVitalSigns(
            UUID encounterId, UUID patientId, UUID surgeryOrderId,
            double temperatureC, double systolicMmHg, double diastolicMmHg,
            double pulseBpm, double respiratoryRate, double spo2Pct,
            double weightKg, double heightCm,
            String avpu, Double bloodGlucoseMmol,
            UUID recordedBy, Instant occurredAt
    ) {
        RecordVitalSignsCommand command = new RecordVitalSignsCommand(
                encounterId, patientId, VitalSignsContext.POST_OP, surgeryOrderId,
                temperatureC, systolicMmHg, diastolicMmHg, pulseBpm, respiratoryRate, spo2Pct,
                weightKg, heightCm, avpu == null ? null : Avpu.valueOf(avpu), bloodGlucoseMmol,
                null, null, null, recordedBy, occurredAt);
        VitalSigns saved = recordVitalSignsService.record(command);
        return saved.id();
    }

    @Override
    public UUID appendAllergyRecord(UUID patientId, UUID encounterId, String description, UUID recordedBy, String source) {
        AllergyRecord saved = allergyRecordRepository.insert(
                AllergyRecord.record(patientId, encounterId, description, null, null,
                        AllergySource.valueOf(source), recordedBy, null));
        return saved.id();
    }

    @Override
    public UUID openConsultationRecord(UUID encounterId, UUID authorId) {
        ConsultationRecord record = openConsultationRecordService.open(encounterId, authorId);
        return record.id();
    }

    @Override
    public void finalizeConsultationRecord(UUID encounterId, UUID authorId, boolean retrospectiveCompletion, String retrospectiveNote) {
        finalizeConsultationRecordService.finalize(encounterId, authorId, retrospectiveCompletion, retrospectiveNote);
    }

    @Override
    public List<AllergySummary> findAllergies(UUID patientId) {
        return allergyRecordRepository.findByPatientId(patientId).stream()
                .map(record -> new AllergySummary(record.description(), record.reaction(), record.severity(),
                        record.confirmationStatus().name()))
                .toList();
    }

    @Override
    public Optional<Double> findLatestWeightKg(UUID encounterId) {
        // The latest reading that actually carries a weight — a consultation
        // re-measure may omit weight (V82), and that must not hide the triage
        // weight the pharmacy needs for weight-based dosing.
        return vitalSignsRepository.findByEncounterId(encounterId).stream()
                .filter(v -> v.weightKg() != null)
                .max(Comparator.comparing(VitalSigns::recordedAt))
                .map(VitalSigns::weightKg);
    }
}
