package com.adags.modules.encounters.application;

// 02-billing-architecture.md §8.1: a patient in AWAITING_PAYMENT who
// cannot pay leaves — the reason lands with the voided charge and the
// encounter event, not silently.
public class MissingAbandonmentReasonException extends RuntimeException {

    public MissingAbandonmentReasonException() {
        super("A reason is required when a patient leaves without paying.");
    }
}
