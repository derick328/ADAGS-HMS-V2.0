package com.adags.modules.billing.domain;

// 07-doctor.md §3's shared-picker configurations. CONSULTATION and now
// PHARMACY have real callers (triage's consultation charge,
// PlacePrescriptionService's prescription charge); PROCEDURE is modelled
// ahead of the module that will use it, the same reasoning as
// EncounterState modelling its full transition graph early.
public enum ServiceItemCategory {
    CONSULTATION,
    LAB,
    PROCEDURE,
    PHARMACY,
    // 04-procedure-bedday-rules.md §3.5: bed-day accrual. Priced from
    // Ward.daily_rate_minor (snapshotted onto WardChargeAccrual at posting
    // time), not resolved from PriceListEntry the way every other category
    // here is — see ChargePostingService.postFixedAmountCharge.
    WARD
}
