package com.adags.modules.laboratory.domain;

import java.util.UUID;

// 16-lab-verification-model.md v0.2 §2: verification only applies to a
// result whose verification_state is PENDING — NOT_REQUIRED has nothing to
// verify, VERIFIED/AMENDED/LAPSED have already left that state.
public class ResultNotPendingVerificationException extends RuntimeException {

    public ResultNotPendingVerificationException(UUID resultId) {
        super("Lab result " + resultId + " is not pending verification.");
    }
}
