package com.adags.modules.encounters.application;

import com.adags.modules.encounters.domain.EncounterState;

public class InvalidEncounterTransitionException extends RuntimeException {

    public InvalidEncounterTransitionException(EncounterState from, EncounterState to) {
        super("Cannot transition encounter from " + from + " to " + to + ".");
    }
}
