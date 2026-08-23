package com.adags.modules.surgery.domain;

import java.util.List;
import java.util.UUID;

public interface SurgicalChecklistRepository {

    SurgicalChecklist insert(SurgicalChecklist checklist);

    List<SurgicalChecklist> findBySurgeryOrderId(UUID surgeryOrderId);

    /**
     * True if any row for (surgeryOrderId, phase) satisfies the gate —
     * for SIGN_IN/TIME_OUT that's any row at all; for SIGN_OUT only a row
     * with count_correct = TRUE (SurgicalChecklist.satisfiesGate).
     */
    boolean isPhaseSatisfied(UUID surgeryOrderId, ChecklistPhase phase);
}
