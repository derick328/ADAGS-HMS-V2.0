package com.adags.modules.inventory.application;

import java.util.UUID;

public record ProposePriceChangeCommand(UUID itemId, long proposedPriceMinor, String justification, UUID proposedBy) {
}
