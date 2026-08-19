package com.adags.modules.reporting.application;

// One row per insurance_claim.state present in the period — "so the
// accountant can see what is owed by whom and chase rejected claims."
public record ClaimsSummaryLine(String state, int count, long totalInsuranceAmountMinor, String currencyCode) {
}
