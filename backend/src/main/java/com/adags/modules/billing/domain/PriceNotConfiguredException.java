package com.adags.modules.billing.domain;

// root CLAUDE.md "Billing": "A missing price for the applicable payer
// blocks the transition with an explicit error — it never defaults to
// zero." Same "loud gap" pattern as VitalReferenceRangeNotConfiguredException:
// refuse rather than fall back, and name exactly what's missing so it can
// be fixed by adding a PriceListEntry, not by debugging a silent zero charge.
public class PriceNotConfiguredException extends RuntimeException {

    public PriceNotConfiguredException(String message) {
        super(message);
    }
}
