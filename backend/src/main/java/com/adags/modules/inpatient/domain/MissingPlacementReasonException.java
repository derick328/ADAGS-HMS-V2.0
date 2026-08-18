package com.adags.modules.inpatient.domain;

import java.util.UUID;

public class MissingPlacementReasonException extends RuntimeException {

    private final UUID bedId;

    public MissingPlacementReasonException(UUID bedId) {
        super("A placement reason is required when the assigned bed's ward class is above the admission's ordered class: bed "
                + bedId);
        this.bedId = bedId;
    }

    public UUID bedId() {
        return bedId;
    }
}
