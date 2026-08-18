package com.adags.modules.inpatient.domain;

// Occupancy is never stored here — it's derived from whether a BedAssignment
// with released_at IS NULL exists for this bed (root CLAUDE.md "Data": a
// materialised counter drifts; the partial unique index is the only source
// of truth). status covers facility-managed states, not occupancy.
public enum BedStatus {
    AVAILABLE,
    OUT_OF_SERVICE
}
