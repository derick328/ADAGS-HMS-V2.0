package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.LabResultParameterValue;
import com.adags.modules.laboratory.domain.ResultFlag;

import java.util.UUID;

public record ParameterValueResponse(
        UUID parameterId,
        double resultValue,
        ResultFlag flag,
        boolean flagOverridden,
        String overrideReason
) {
    public static ParameterValueResponse from(LabResultParameterValue value) {
        return new ParameterValueResponse(
                value.parameterId(), value.resultValue(), value.flag(), value.flagOverridden(), value.overrideReason());
    }
}
