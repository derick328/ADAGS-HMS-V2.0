package com.adags.modules.referencedata.domain;

import java.util.Optional;

public interface Icd10CodeRepository {

    Optional<Icd10Code> findByCode(String code);

    /**
     * Insert-or-update by code, never touching isActive (§4.1: import
     * happens ahead of any curation; a re-import of a corrected release
     * must not silently reactivate a code a clinical lead deliberately
     * retired, nor deactivate one that was never touched). Returns whether
     * the row was newly inserted (true) or updated (false).
     */
    boolean upsert(Icd10Code code);

    ImportStatus upsertWhoRow(String code, String title, String chapter, String block,
                              String parentCode, String release, String entryType,
                              String usageMarker, String longTitle, boolean selectable);

    enum ImportStatus { INSERTED, UPDATED, SKIPPED }

    /** Root CLAUDE.md "Data": retirement, never deletion. */
    void deactivate(String code);
}
