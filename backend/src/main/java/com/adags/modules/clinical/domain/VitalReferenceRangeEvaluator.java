package com.adags.modules.clinical.domain;

// Plain function, no Spring, no I/O — the caller resolves the applicable
// VitalReferenceRange (a repository lookup) and passes it in here.
public final class VitalReferenceRangeEvaluator {

    private VitalReferenceRangeEvaluator() {
    }

    public static VitalEvaluation evaluate(VitalType vitalType, double value, VitalReferenceRange range) {
        if (range.vitalType() != vitalType) {
            throw new IllegalArgumentException(
                    "Range is for " + range.vitalType() + ", not " + vitalType + " — caller passed the wrong range.");
        }
        if (!range.isConfigured()) {
            throw new VitalReferenceRangeNotConfiguredException(vitalType, range.ageBand());
        }
        if (value < range.implausibleLow()) {
            throw new ImplausibleVitalException(vitalType, value, true, range.implausibleLow());
        }
        if (value > range.implausibleHigh()) {
            throw new ImplausibleVitalException(vitalType, value, false, range.implausibleHigh());
        }

        boolean abnormal = (range.abnormalLow() != null && value < range.abnormalLow())
                || (range.abnormalHigh() != null && value > range.abnormalHigh());
        return new VitalEvaluation(vitalType, value, abnormal);
    }
}
