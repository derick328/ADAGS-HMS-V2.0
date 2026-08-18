package com.adags.modules.pharmacy.application;

import java.util.UUID;

public record ProposeSubstitutionCommand(UUID prescriptionLineId, UUID proposedItemId, String reason, UUID proposedBy) {
}
