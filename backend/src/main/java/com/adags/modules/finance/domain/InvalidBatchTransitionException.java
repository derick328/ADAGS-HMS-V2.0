package com.adags.modules.finance.domain;

import java.util.UUID;

public class InvalidBatchTransitionException extends RuntimeException {

    public InvalidBatchTransitionException(UUID batchId, BatchStatus from, String action) {
        super("Cannot " + action + " batch " + batchId + " — it is " + from + ".");
    }
}
