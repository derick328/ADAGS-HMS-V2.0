package com.adags.modules.inpatient.api;

import java.util.UUID;

public record NurseWorkloadResponse(UUID nurseId, String username, int activePatients) {
}
