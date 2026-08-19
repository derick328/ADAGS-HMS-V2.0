package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.WardStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateWardStatusRequest(@NotNull WardStatus wardStatus) {
}
