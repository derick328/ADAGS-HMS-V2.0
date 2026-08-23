package com.adags.modules.surgery.domain;

import java.util.List;
import java.util.UUID;

public interface AnaesthesiaPaperRecordRepository {

    AnaesthesiaPaperRecord insert(AnaesthesiaPaperRecord record);

    List<AnaesthesiaPaperRecord> findBySurgeryOrderId(UUID surgeryOrderId);

    /** True if any row exists for this order — either a real pointer or a no-anaesthesia declaration. */
    boolean existsForSurgeryOrder(UUID surgeryOrderId);
}
