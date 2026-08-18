package com.adags.modules.inpatient.domain;

import java.util.UUID;

public class DischargeNotOrderedException extends RuntimeException {

    public DischargeNotOrderedException(UUID admissionId) {
        super("Admission " + admissionId + " has no discharge order to complete.");
    }
}
