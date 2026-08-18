package com.adags.shared.kernel;

// 01-architecture.md §9.1: "Integer minor units plus a currency code.
// Never floating point... A Money value object in the shared kernel; no
// raw arithmetic on amounts anywhere else." First real caller is
// PriceListEntry (billing module, Phase 3 item 1) — every money-bearing
// column from here on (Invoice, Payment, ...) composes this rather than
// carrying amount_minor/currency_code as loose primitives.
//
// No sign constraint here: a price must be >= 0, a payment must be > 0, a
// credit note is legitimately negative — those are caller-specific rules
// (enforced at the DB and the domain layer that owns each case), not a
// property of "an amount of money" in general.
public record Money(long amountMinor, String currencyCode) {

    public Money {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode is required.");
        }
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amountMinor + other.amountMinor, currencyCode);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amountMinor - other.amountMinor, currencyCode);
    }

    public Money times(long quantity) {
        return new Money(amountMinor * quantity, currencyCode);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amountMinor > other.amountMinor;
    }

    private void requireSameCurrency(Money other) {
        if (!currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException(
                    "Cannot combine " + currencyCode + " with " + other.currencyCode + ".");
        }
    }
}
