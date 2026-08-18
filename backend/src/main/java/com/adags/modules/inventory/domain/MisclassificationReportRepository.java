package com.adags.modules.inventory.domain;

import java.util.List;

public interface MisclassificationReportRepository {

    /**
     * Items with schedule UNCLASSIFIED or PRESCRIPTION_ONLY whose name
     * trigram-matches a {@link NarcoticWatchlistEntry}, similarity strictly
     * above {@code threshold}, best match first. Empty list, not an
     * exception, when the watchlist is empty — an unseeded watchlist means
     * nothing to compare against, not a system failure.
     */
    List<MisclassificationCandidate> findCandidates(double threshold);
}
