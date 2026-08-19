package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §10/§11. encounterId is nullable — a pharmacy
// walk-in or a records fee has no encounter. insurancePolicyId carries no
// FK yet: the insurance module doesn't exist.
//
// Two-step issue (§10): draft() below produces a DRAFT — freely editable,
// not a financial record, no invoiceNumber. InvoiceRepository.issue
// allocates the number from the gapless sequence and locks the lines;
// never at draft. version is optimistic-locked per backend/CLAUDE.md.
public record Invoice(
        UUID id,
        UUID encounterId,
        UUID patientId,
        UUID facilityId,
        InvoiceState state,
        PayerCategory payerCategory,
        UUID insurancePolicyId,
        /** Null until ISSUED — assigned from the gapless per-facility per-year sequence at issue, never at draft. */
        String invoiceNumber,
        Money grossTotal,
        Money insurancePortionTotal,
        Money patientPortionTotal,
        /**
         * 02-billing-architecture.md §9.3: total − payments_settled −
         * credit_notes − write_offs − insurance_approved + refunds.
         * Materialised here, not recomputed on every read — credit_notes,
         * write_offs, insurance_approved and refunds are always 0 today
         * (none of those mechanisms exist yet), so in practice this is
         * grossTotal minus settled payments; each of those, once built,
         * plugs into the same column. Zero at DRAFT (nothing owed before
         * issue), set to grossTotal at issue, decremented by
         * InvoiceRepository.recordSettlement as payments settle.
         */
        Money outstandingTotal,
        /** Non-null only when state == VOID — set together, never independently. */
        String voidReason,
        UUID voidedBy,
        Instant voidedAt,
        /**
         * Set once, when markReceivablePostDischarge fires — the aging
         * clock for the aged-receivables report (18-admin.md §10). Never
         * touched again; a partial payment or write-off does not un-age a
         * receivable. Null for every invoice that never became one.
         */
        Instant receivableAt,
        int version,
        Instant createdAt,
        UUID createdBy
) {

    private static final String CURRENCY_CODE = "TZS";

    public static Invoice draft(UUID encounterId, UUID patientId, UUID facilityId, PayerCategory payerCategory,
                                 UUID insurancePolicyId, UUID createdBy) {
        Money zero = new Money(0, CURRENCY_CODE);
        return new Invoice(null, encounterId, patientId, facilityId, InvoiceState.DRAFT, payerCategory,
                insurancePolicyId, null, zero, zero, zero, zero, null, null, null, null, 0, null, createdBy);
    }
}
