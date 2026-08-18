package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.ItemClassificationChangeRepository;
import com.adags.modules.inventory.domain.MisclassificationReportRepository;
import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Service;

// 12-pharmacist.md §10.6. Read-only — "routed to CLINICAL_LEAD and the
// pharmacist-in-charge" monthly per the spec, but no notifications/
// reporting-module infrastructure exists yet in this codebase (both are
// unbuilt phases per backend/CLAUDE.md's module list), so this is exposed
// as an on-demand query today rather than a scheduled push — same honest-
// stub treatment as BillingGateImpl.verifiedInsuranceCovers.
@Service
public class MisclassificationDetectionReportService {

    private static final String THRESHOLD_KEY = "pharmacy.misclassification_detection.similarity_threshold";

    private final MisclassificationReportRepository misclassificationReportRepository;
    private final ItemClassificationChangeRepository changeRepository;
    private final ProtocolSettingReader protocolSettingReader;

    public MisclassificationDetectionReportService(
            MisclassificationReportRepository misclassificationReportRepository,
            ItemClassificationChangeRepository changeRepository,
            ProtocolSettingReader protocolSettingReader
    ) {
        this.misclassificationReportRepository = misclassificationReportRepository;
        this.changeRepository = changeRepository;
        this.protocolSettingReader = protocolSettingReader;
    }

    public MisclassificationReport generate() {
        double threshold = protocolSettingReader.getDouble(THRESHOLD_KEY);
        return new MisclassificationReport(
                misclassificationReportRepository.findCandidates(threshold),
                changeRepository.findControlledReleases());
    }
}
