package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.CriticalResultNotification;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultParameterValue;

import java.util.List;

public record RecordLabResultResult(
        LabResult result,
        List<LabResultParameterValue> parameterValues,
        List<CriticalResultNotification> criticalNotifications
) {
}
