package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WardChargeAccrualRepository {

    List<WardChargeAccrual> findByAdmissionId(UUID admissionId);

    /** MAX(accrual_boundary_at) among completed (invoice_line_id set) rows for this admission, if any. */
    Optional<Instant> findLastPostedBoundary(UUID admissionId);

    /**
     * Claims (bedAssignmentId, accrualBoundaryAt) with invoiceLineId left
     * null — throws DuplicateAccrualException if another row already
     * holds that slot. Call attachInvoiceLine to complete it; both must
     * happen in the same transaction as the ChargePostingService call
     * between them, so a failure anywhere rolls the reservation back too.
     */
    WardChargeAccrual reserve(WardChargeAccrual accrual);

    void attachInvoiceLine(UUID accrualId, UUID invoiceLineId);
}
