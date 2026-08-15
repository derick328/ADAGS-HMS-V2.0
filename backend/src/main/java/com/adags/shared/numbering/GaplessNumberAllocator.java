package com.adags.shared.numbering;

import java.util.UUID;

// root CLAUDE.md "Numbering": gapless per-facility per-year sequences with
// row locking, never MAX(n)+1, never a UUID for a human-facing number.
// Shared rather than patient-specific — hospital numbers are the first
// caller, but invoice, accession and claim numbers need the exact same
// mechanism later.
public interface GaplessNumberAllocator {

    /**
     * Allocates the next value in the (sequenceName, facilityId, year) counter
     * and returns it formatted as "{facility code}-{year}-{zero-padded value}".
     */
    String allocate(String sequenceName, UUID facilityId, int year, int padWidth);
}
