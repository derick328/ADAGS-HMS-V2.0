package com.adags.modules.finance.api;

import jakarta.validation.constraints.NotBlank;

public record ReverseBatchRequest(@NotBlank String reason) {
}
