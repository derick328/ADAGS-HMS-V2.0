package com.adags.modules.reporting.application;

import java.util.UUID;

// One ranked catalogue item inside a revenue category — a medicine, a lab
// test, a procedure. Everything here is billing data: the figures come
// from invoice_line and payment_allocation, never from stock_movement or
// dispense_line, so this is what was sold, not what left the shelf.
//
// quantityBilled is SUM(invoice_line.quantity) for lines billed in the
// period — the quantity CHARGED. For pharmacy that is the prescribed
// quantity the patient was billed for (PlacePrescriptionService posts the
// charge at prescribing time, and dispensing is gated on it being paid);
// it is not a dispensed-quantity count.
//
// settledLineCount counts lines BILLED in the period whose patient portion
// is fully settled as of now — "paid tests" in the sense an admin means.
// It is a cohort measure over the billed lines, deliberately not
// period-bounded on the payment date, so it can be read against
// billedLineCount on the same row.
//
// strength/form are present only for pharmacy items and only where
// service_item.code matches item.code. That is a code correspondence
// (both columns are UNIQUE, so the join is 1:1), NOT a foreign key —
// the billing catalogue and the stock catalogue are separate tables with
// no declared relationship. Null means no matching stock item, never
// "no strength".
public record TopServiceItemLine(
        UUID serviceItemId,
        String code,
        String name,
        String strength,
        String form,
        long quantityBilled,
        int billedLineCount,
        int settledLineCount,
        long billedMinor,
        long collectedMinor,
        String currencyCode
) {
}
