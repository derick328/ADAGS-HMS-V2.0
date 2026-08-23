package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.CancellationReason;
import jakarta.validation.constraints.NotNull;

public record CancelSurgeryOrderRequest(@NotNull CancellationReason reason) {
}
