package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.NarcoticWatchlistEntry;
import com.adags.modules.inventory.domain.NarcoticWatchlistEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 12-pharmacist.md §10.6, 14-controlled-medicines-tanzania.md §2.1: the
// watchlist is curated by a human with ITEM_CLASSIFY authority transcribing
// the regulator's First Schedule — this service does not, and cannot,
// validate that an entry belongs on the narcotic list (that would require
// hardcoding exactly the drug-name knowledge the build brief says not to
// seed). sourceDocument is mandatory so every entry is traceable.
@Service
public class AddWatchlistEntryService {

    private final NarcoticWatchlistEntryRepository repository;

    public AddWatchlistEntryService(NarcoticWatchlistEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NarcoticWatchlistEntry add(AddWatchlistEntryCommand command) {
        NarcoticWatchlistEntry entry = NarcoticWatchlistEntry.add(command.innName(), command.atcPrefix(),
                command.sourceDocument(), command.sourceDate(), command.actorUserId());
        return repository.insert(entry);
    }
}
