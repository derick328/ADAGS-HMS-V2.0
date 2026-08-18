package com.adags.modules.billing.domain;

import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository {

    PaymentAllocation insert(PaymentAllocation allocation);

    /** Only allocations funded by a SETTLED payment — a PENDING_CLEARANCE cheque contributes nothing yet. */
    List<PaymentAllocation> findSettledByInvoiceLineId(UUID invoiceLineId);
}
