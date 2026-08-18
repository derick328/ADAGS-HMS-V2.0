package com.adags.modules.inventory.api;

import java.util.List;

public record WatchlistListResponse(String curationGuidance, List<WatchlistEntryResponse> entries) {

    // 14-controlled-medicines-tanzania.md §3.4, 12-pharmacist.md §10.6:
    // regulation 16(3) places these in Prescription Only, not Controlled —
    // "matching against those would produce constant false positives on
    // diazepam and midazolam." Surfaced on every fetch of the curation
    // screen's data, not just in a code comment, because whoever curates
    // will otherwise add them on instinct — the report then fires monthly
    // on normal stock until someone turns it off, and it's off on the day
    // it matters.
    private static final String GUIDANCE =
            "Regulation 16(3) places moderate/high-therapeutic-value psychotropics in Prescription Only, "
                    + "NOT Controlled — do not add diazepam, midazolam, lorazepam, clonazepam, alprazolam, "
                    + "chlordiazepoxide, nitrazepam, oxazepam, temazepam, triazolam, flunitrazepam, phenobarbital, "
                    + "methylphenobarbital, pentobarbital, secobarbital, amobarbital, buprenorphine, pentazocine, "
                    + "phentermine, zolpidem, dronabinol, meprobamate, or methaqualone to this watchlist. "
                    + "Only First Schedule narcotics and limited-value psychotropics belong here.";

    public static WatchlistListResponse of(List<WatchlistEntryResponse> entries) {
        return new WatchlistListResponse(GUIDANCE, entries);
    }
}
