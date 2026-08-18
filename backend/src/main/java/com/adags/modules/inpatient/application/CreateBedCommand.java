package com.adags.modules.inpatient.application;

import java.util.UUID;

public record CreateBedCommand(UUID wardId, String label, String bedType) {
}
