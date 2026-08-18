package com.adags.modules.inventory.application;

import java.util.UUID;

public record ProposeNewItemCommand(
        String proposedName, String proposedClassification, String proposedForm, String proposedUnit,
        long proposedPriceMinor, String justification, UUID proposedBy
) {
}
