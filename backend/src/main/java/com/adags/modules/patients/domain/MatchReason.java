package com.adags.modules.patients.domain;

public enum MatchReason {
    /** Absolute block, never overridable — resolve by opening the existing record. */
    NATIONAL_ID_EXACT,
    /** Block, overridable with a logged "not the same person" reason. */
    NAME_SIMILARITY_WITH_DOB_MATCH,
    /** Warn only — proceeding is always allowed. */
    NAME_SIMILARITY,
    /** Warn only. Phone is not unique (families share numbers), so this never blocks alone. */
    PHONE_EXACT
}
