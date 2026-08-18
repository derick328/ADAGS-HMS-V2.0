package com.adags.modules.clinical.domain;

public record VitalEvaluation(VitalType vitalType, double value, boolean abnormal) {
}
