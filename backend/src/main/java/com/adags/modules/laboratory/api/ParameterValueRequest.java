package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.ResultFlag;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ParameterValueRequest(
        @NotNull UUID parameterId,
        @NotNull Double resultValue,
        ResultFlag overrideFlag,
        String overrideReason
) {
}
