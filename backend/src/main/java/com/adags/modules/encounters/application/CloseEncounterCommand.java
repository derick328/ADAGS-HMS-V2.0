package com.adags.modules.encounters.application;

import com.adags.modules.encounters.domain.EncounterState;

import java.util.UUID;

// 05-receptionist.md §5.3 "Dismiss" — the three outcomes CloseEncounterService
// accepts are LEFT_WITHOUT_BEING_SEEN, CANCELLED_IN_ERROR, and CLOSED. Any
// other target is rejected before the transition graph is even consulted;
// this command is reception's dismiss action, not a general state-setter.
public record CloseEncounterCommand(
        UUID encounterId,
        EncounterState outcome,
        /** Required for CANCELLED_IN_ERROR; optional otherwise. */
        String reason,
        UUID actorUserId
) {
}
