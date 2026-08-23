package com.adags.modules.surgery.domain;

// 04-procedure-bedday-rules.md §2.4: "Procedure COMPLETE -> disposition."
public enum SurgeryDisposition {
    RECOVERY_THEN_DISCHARGE,
    ADMIT_TO_WARD,
    TRANSFER_OUT,
    DECEASED
}
