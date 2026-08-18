package com.adags.modules.encounters.api;

import com.adags.modules.encounters.domain.EncounterState;
import jakarta.validation.constraints.NotNull;

// outcome is restricted to LEFT_WITHOUT_BEING_SEEN, CANCELLED_IN_ERROR, or
// CLOSED by CloseEncounterService — not by validation here, so the error
// message names the actual accepted set rather than "invalid value".
public record CloseEncounterRequest(
        @NotNull EncounterState outcome,
        String reason
) {
}
