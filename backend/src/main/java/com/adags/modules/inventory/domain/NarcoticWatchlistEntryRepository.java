package com.adags.modules.inventory.domain;

import java.util.List;

public interface NarcoticWatchlistEntryRepository {

    NarcoticWatchlistEntry insert(NarcoticWatchlistEntry entry);

    List<NarcoticWatchlistEntry> findAll();
}
