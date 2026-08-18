package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.domain.VitalReferenceRange;
import com.adags.modules.clinical.domain.VitalReferenceRangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// 06-triage.md §2.2: paediatric VitalReferenceRange bands are seeded with
// NULL bounds rather than adult fallback values (see V9__vital_reference_range.sql).
// This makes that gap loud and visible at startup instead of silent —
// triage submissions for an affected age band will fail with
// VitalReferenceRangeNotConfiguredException regardless of whether this
// warning is read, but nobody should have to hit that in production to
// find out. Does not block startup — the app must still run for every
// other module.
@Component
public class ClinicalStartupChecks implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClinicalStartupChecks.class);

    private final VitalReferenceRangeRepository vitalReferenceRangeRepository;

    public ClinicalStartupChecks(VitalReferenceRangeRepository vitalReferenceRangeRepository) {
        this.vitalReferenceRangeRepository = vitalReferenceRangeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<VitalReferenceRange> unconfigured = vitalReferenceRangeRepository.findUnconfigured();
        if (unconfigured.isEmpty()) {
            return;
        }
        log.warn("{} VitalReferenceRange band(s) have no implausible/abnormal bounds configured — "
                        + "triage will refuse to record any of these vitals until a clinician populates them: {}",
                unconfigured.size(), describe(unconfigured));
    }

    private String describe(List<VitalReferenceRange> unconfigured) {
        return unconfigured.stream()
                .map(range -> range.vitalType() + "/" + range.ageBand() + "/" + range.sex())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
