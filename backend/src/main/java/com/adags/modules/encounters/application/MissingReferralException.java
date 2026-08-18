package com.adags.modules.encounters.application;

// 06-triage.md §5: "Confirmed: referral can never be blank." No assigned
// provider means no price to resolve and nothing for the billing gate to
// check later — rejected outright here, never defaulted to some fallback
// doctor.
public class MissingReferralException extends RuntimeException {

    public MissingReferralException() {
        super("A referral (assigned provider) is required to submit a triage assessment.");
    }
}
