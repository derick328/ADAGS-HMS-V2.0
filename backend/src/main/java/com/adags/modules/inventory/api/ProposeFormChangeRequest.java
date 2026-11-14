package com.adags.modules.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProposeFormChangeRequest(@NotNull UUID itemId, @NotBlank String proposedForm,
                                       String proposedUnit,
                                       @NotBlank String justification) {}
