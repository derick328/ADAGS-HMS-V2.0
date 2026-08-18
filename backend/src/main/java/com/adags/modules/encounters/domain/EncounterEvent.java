package com.adags.modules.encounters.domain;

import java.time.Instant;
import java.util.UUID;

// Append-only — 01-architecture.md §2.1 rule 4: "audit trail, queue-time
// analytics, and debugging tool in one table." One row per transition,
// written in the same transaction as the state change it records.
public record EncounterEvent(
        UUID id,
        UUID encounterId,
        /** Null only for the first event on an encounter (creation). */
        EncounterState fromState,
        EncounterState toState,
        UUID actorUserId,
        Instant occurredAt,
        String reason
) {
}
