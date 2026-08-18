package com.adags.modules.inpatient.domain;

import java.util.UUID;

// 04-procedure-bedday-rules.md §3.6: "Doctor, OR the nurse that doctor
// assigned to this patient." DISCHARGE_ORDER held as a permission is not
// enough on its own for a nurse — this is the assignment-scoping check
// that makes it "her own doctor's patients, not every patient on the
// ward," 17-ward-nurse.md §1 ⚠1's care-relationship reasoning applied to
// discharge specifically.
public class DischargeNotAuthorizedException extends RuntimeException {

    public DischargeNotAuthorizedException(UUID actorUserId, UUID admissionId) {
        super("User " + actorUserId + " may not order or complete discharge for admission " + admissionId
                + " — not a doctor and not on the nursing roster for this admission.");
    }
}
