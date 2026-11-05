package com.adags.modules.patients.domain;

public enum MatchReason {
    /** Exact national ID match: open the existing record. */
    NATIONAL_ID_EXACT,
    /** Case- and whitespace-normalized full-name match. */
    FULL_NAME_EXACT,
    /** Retained for compatibility with older API responses. */
    NAME_SIMILARITY_WITH_DOB_MATCH,
    /** Similar full name, including middle name when supplied: open the existing record. */
    NAME_SIMILARITY,
    /** Retained for compatibility with older API responses. */
    PHONE_EXACT
}
