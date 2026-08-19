package com.adags.modules.inpatient.domain;

// 17-ward-nurse.md §9: DISCHARGED is owned by the discharge flow and
// TRANSFERRED by §8.3 ward transfer (not built in this item) — a nurse
// cannot set either through UpdateWardStatusService.
public class WardStatusNotSettableException extends RuntimeException {

    public WardStatusNotSettableException(WardStatus attempted) {
        super(attempted + " cannot be set directly — it is owned by a dedicated flow, not the ward status update endpoint.");
    }
}
