package com.adags.modules.laboratory.domain;

// 15-lab-technician.md §5.1/§5.2: N/H/L/C, computed by LabResultFlagEvaluator
// — "with ranges from the catalogue, H/L/N/C is computed. That is more
// reliable than a technician at 2am, and it is what makes critical
// alerting (§6/§7) possible at all."
public enum ResultFlag {
    NORMAL,
    HIGH,
    LOW,
    CRITICAL
}
