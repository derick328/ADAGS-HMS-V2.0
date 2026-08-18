package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// 09-doctor-incomplete-consultations.md §2. Threshold is
// protocol_setting-backed (`consultation.incomplete_prompt_hours`, default
// 12), not a constant — same ProtocolSettingReader mechanism as the triage
// pregnancy-prompt bounds and the patient duplicate-detection thresholds.
@Service
public class IncompleteConsultationService {

    private static final String THRESHOLD_HOURS_KEY = "consultation.incomplete_prompt_hours";

    private final ConsultationRecordRepository repository;
    private final ProtocolSettingReader protocolSettingReader;
    private final Clock clock;

    public IncompleteConsultationService(
            ConsultationRecordRepository repository, ProtocolSettingReader protocolSettingReader, Clock clock
    ) {
        this.repository = repository;
        this.protocolSettingReader = protocolSettingReader;
        this.clock = clock;
    }

    public List<ConsultationRecord> findIncomplete(UUID doctorId) {
        double thresholdHours = protocolSettingReader.getDouble(THRESHOLD_HOURS_KEY);
        Instant threshold = Instant.now(clock).minus((long) (thresholdHours * 60), ChronoUnit.MINUTES);
        return repository.findIncomplete(doctorId, threshold);
    }
}
