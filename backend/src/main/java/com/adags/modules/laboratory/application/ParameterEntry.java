package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.ResultFlag;

import java.util.UUID;

// 15-lab-technician.md §5.2: "flag ... Computed ... Override permitted with
// a reason." overrideFlag is null for the normal case (accept the computed
// flag); non-null only when the technician disagrees and supplies overrideReason.
public record ParameterEntry(UUID parameterId, double resultValue, ResultFlag overrideFlag, String overrideReason) {
}
