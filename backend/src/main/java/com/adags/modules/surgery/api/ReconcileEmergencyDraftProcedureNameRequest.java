package com.adags.modules.surgery.api;

import jakarta.validation.constraints.NotNull;

public record ReconcileEmergencyDraftProcedureNameRequest(@NotNull String procedureServiceItemCode) {
}
