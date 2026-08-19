package com.adags.modules.inventory.application;

import java.util.UUID;

public record SetAdministrationWitnessCommand(UUID itemId, boolean requiresWitness) {
}
