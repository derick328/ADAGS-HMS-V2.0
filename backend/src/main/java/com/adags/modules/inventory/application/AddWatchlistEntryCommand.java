package com.adags.modules.inventory.application;

import java.time.LocalDate;
import java.util.UUID;

public record AddWatchlistEntryCommand(
        String innName,
        String atcPrefix,
        String sourceDocument,
        LocalDate sourceDate,
        UUID actorUserId
) {
}
