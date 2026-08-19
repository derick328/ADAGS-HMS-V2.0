package com.adags.modules.insurance.domain;

// root CLAUDE.md "Billing": "A missing price... blocks with an explicit
// error — it never defaults to zero." Same discipline for coverage:
// guessing EXCLUDED or FULL when no rule is configured mis-prices the
// patient or the scheme in one direction or the other — refuse instead,
// same treatment as billing.domain.PriceNotConfiguredException.
public class CoverageRuleNotConfiguredException extends RuntimeException {

    public CoverageRuleNotConfiguredException(String message) {
        super(message);
    }
}
