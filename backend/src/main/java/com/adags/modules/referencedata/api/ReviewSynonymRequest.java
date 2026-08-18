package com.adags.modules.referencedata.api;

// review_note is optional on approve, effectively required on reject in
// practice — not enforced here because the spec doesn't make it a hard
// rule, only that a review always has a note field available.
public record ReviewSynonymRequest(String reviewNote) {
}
