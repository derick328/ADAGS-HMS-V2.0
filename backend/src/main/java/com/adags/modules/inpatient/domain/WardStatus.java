package com.adags.modules.inpatient.domain;

// 17-ward-nurse.md §9. DISCHARGED stays owned by the discharge flow
// (OrderDischargeService/CompleteDischargeService) and TRANSFERRED by §8.3
// ward transfer — neither is settable through UpdateWardStatusService,
// which only accepts ADMITTED/STABLE/CRITICAL from a nurse.
public enum WardStatus {
    ADMITTED,
    STABLE,
    CRITICAL,
    DISCHARGED,
    TRANSFERRED
}
