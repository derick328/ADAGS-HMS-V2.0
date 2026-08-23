package com.adags.modules.encounters.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriageAssessmentDraftRepository {

    /** Upsert by (encounter_id, author_id) — the whole point of autosave. */
    TriageAssessmentDraft upsert(UUID encounterId, UUID authorId, TriagePriority priority,
                                  ModeOfAmbulation modeOfAmbulation, String formStateJson);

    /** The calling nurse's own draft, for resuming the form (possibly on another device). */
    Optional<TriageAssessmentDraft> findByEncounterAndAuthor(UUID encounterId, UUID authorId);

    /**
     * The most recently updated draft per encounter, across every author —
     * the queue's "being assessed by" advisory projection (§3). More than
     * one nurse may hold a live draft for the same encounter; this
     * surfaces only the latest, since the queue shows one advisory line
     * per row, not a list.
     */
    List<TriageAssessmentDraft> findLatestByEncounterIds(Collection<UUID> encounterIds);
}
