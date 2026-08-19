package com.adags.modules.insurance.domain;

import com.adags.shared.kernel.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 02-billing-architecture.md §6.2: "Rules are versioned... a claim
// submitted in November must be assessable against the rule that applied
// on the service date in September — never today's rule." Same
// effective_from/effective_to versioning as PriceListEntry, but — unlike
// that table — genuinely resolved as-of an arbitrary past date, since a
// claim is assembled after the service was already rendered.
//
// Exactly one of serviceCategory/serviceItemId is set: a category rule
// (the general case) and an item-specific rule (a named override) are two
// different kinds of row, never both at once on the same row. "A
// service-item-specific rule wins over a category rule for the same
// date" is decided at resolution time (SchemeCoverageRuleRepository
// .findApplicable), not encoded on the row itself.
public record SchemeCoverageRule(
        UUID id,
        UUID schemeId,
        ServiceCategory serviceCategory,
        UUID serviceItemId,
        CoverageType coverageType,
        /** 0-100 for PERCENTAGE, minor units for FIXED_AMOUNT, null for FULL/EXCLUDED. */
        BigDecimal coverageValue,
        boolean requiresPreauth,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        UUID createdBy,
        Instant createdAt
) {

    public static SchemeCoverageRule forCategory(UUID schemeId, ServiceCategory serviceCategory,
                                                  CoverageType coverageType, BigDecimal coverageValue,
                                                  boolean requiresPreauth, UUID createdBy) {
        if (serviceCategory == null) {
            throw new IllegalArgumentException("serviceCategory is required for a category rule.");
        }
        requireValueForType(coverageType, coverageValue);
        return new SchemeCoverageRule(null, schemeId, serviceCategory, null, coverageType, coverageValue,
                requiresPreauth, LocalDate.now(), null, createdBy, null);
    }

    public static SchemeCoverageRule forServiceItem(UUID schemeId, UUID serviceItemId, CoverageType coverageType,
                                                     BigDecimal coverageValue, boolean requiresPreauth, UUID createdBy) {
        if (serviceItemId == null) {
            throw new IllegalArgumentException("serviceItemId is required for an item-specific rule.");
        }
        requireValueForType(coverageType, coverageValue);
        return new SchemeCoverageRule(null, schemeId, null, serviceItemId, coverageType, coverageValue,
                requiresPreauth, LocalDate.now(), null, createdBy, null);
    }

    private static void requireValueForType(CoverageType coverageType, BigDecimal coverageValue) {
        if ((coverageType == CoverageType.PERCENTAGE || coverageType == CoverageType.FIXED_AMOUNT) && coverageValue == null) {
            throw new IllegalArgumentException("coverageValue is required for " + coverageType + ".");
        }
        if (coverageType == CoverageType.PERCENTAGE && coverageValue != null
                && (coverageValue.signum() < 0 || coverageValue.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("A PERCENTAGE coverageValue must be between 0 and 100.");
        }
    }

    // Phase 7 item 3 part 2: the InvoiceLine split this rule produces for
    // a given gross amount. Pure computation, no I/O — unit-testable
    // without a Spring context (backend/CLAUDE.md "Testing").
    //
    //   FULL         insurancePortion = gross,                    patientPortion = 0
    //   PERCENTAGE   insurancePortion = round(gross * value/100),  patientPortion = remainder
    //   FIXED_AMOUNT insurancePortion = min(value, gross),         patientPortion = remainder
    //   EXCLUDED     insurancePortion = 0,                         patientPortion = gross
    //
    // patientPortion is always gross minus insurancePortion (a remainder),
    // never independently rounded — so the two halves always sum back to
    // gross exactly, with no rounding-gap minor unit unaccounted for.
    public Split computeSplit(Money gross) {
        long grossMinor = gross.amountMinor();
        long insuranceMinor = switch (coverageType) {
            case FULL -> grossMinor;
            case EXCLUDED -> 0L;
            // coverageValue is 0-100 here (V46), not a 0-1 fraction —
            // dividing by 100 is not optional.
            case PERCENTAGE -> BigDecimal.valueOf(grossMinor)
                    .multiply(coverageValue)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            case FIXED_AMOUNT -> Math.min(coverageValue.longValueExact(), grossMinor);
        };
        long patientMinor = grossMinor - insuranceMinor;
        return new Split(new Money(insuranceMinor, gross.currencyCode()), new Money(patientMinor, gross.currencyCode()));
    }

    public record Split(Money insurancePortion, Money patientPortion) {
    }
}
