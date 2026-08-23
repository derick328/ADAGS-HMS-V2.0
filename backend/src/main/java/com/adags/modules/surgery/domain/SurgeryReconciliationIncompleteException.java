package com.adags.modules.surgery.domain;

import java.util.UUID;

// root CLAUDE.md task text: "SurgeryOrder cannot reach COMPLETED without
// reconciliation balanced. COMPLETED cannot be reached without
// reconciliation." Every ProcedureItem prepared against this order must be
// both reconciled (nurse) and attested (surgeon) before COMPLETED —
// 04-procedure-bedday-rules.md §2.3: "PENDING_RECONCILIATION appears on a
// worklist so it cannot be silently skipped."
public class SurgeryReconciliationIncompleteException extends RuntimeException {

    private final UUID surgeryOrderId;

    public SurgeryReconciliationIncompleteException(UUID surgeryOrderId) {
        super("Surgery order " + surgeryOrderId
                + " cannot reach COMPLETED: one or more procedure items are not fully reconciled and attested.");
        this.surgeryOrderId = surgeryOrderId;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }
}
