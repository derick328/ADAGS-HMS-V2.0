package com.adags.modules.laboratory.domain;

// Plain function, no Spring, no I/O — mirrors clinical.domain.
// VitalReferenceRangeEvaluator. Critical is checked before normal: being
// outside the critical bound is, by construction, also outside normal, and
// critical must win (a critical value is CRITICAL, never merely HIGH/LOW).
public final class LabResultFlagEvaluator {

    private LabResultFlagEvaluator() {
    }

    public static ResultFlag evaluate(String parameterCode, double value, LabReferenceRange range) {
        if (!range.isConfigured()) {
            throw new LabReferenceRangeNotConfiguredException(range.parameterId(), range.ageMinMonths(), range.sex());
        }
        if (value < range.implausibleLow()) {
            throw new ImplausibleResultValueException(parameterCode, value, true, range.implausibleLow());
        }
        if (value > range.implausibleHigh()) {
            throw new ImplausibleResultValueException(parameterCode, value, false, range.implausibleHigh());
        }

        boolean belowCritical = range.criticalLow() != null && value < range.criticalLow();
        boolean aboveCritical = range.criticalHigh() != null && value > range.criticalHigh();
        if (belowCritical || aboveCritical) {
            return ResultFlag.CRITICAL;
        }
        if (value < range.normalLow()) {
            return ResultFlag.LOW;
        }
        if (value > range.normalHigh()) {
            return ResultFlag.HIGH;
        }
        return ResultFlag.NORMAL;
    }
}
