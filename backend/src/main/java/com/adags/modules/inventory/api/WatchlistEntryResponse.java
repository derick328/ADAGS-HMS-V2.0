package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.NarcoticWatchlistEntry;

import java.time.LocalDate;
import java.util.UUID;

public record WatchlistEntryResponse(UUID id, String innName, String atcPrefix, String sourceDocument, LocalDate sourceDate) {

    public static WatchlistEntryResponse from(NarcoticWatchlistEntry entry) {
        return new WatchlistEntryResponse(entry.id(), entry.innName(), entry.atcPrefix(), entry.sourceDocument(),
                entry.sourceDate());
    }
}
