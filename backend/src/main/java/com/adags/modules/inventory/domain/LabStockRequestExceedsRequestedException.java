package com.adags.modules.inventory.domain;

import java.util.UUID;

public class LabStockRequestExceedsRequestedException extends RuntimeException {

    public LabStockRequestExceedsRequestedException(UUID requestId, int requested, int remaining) {
        super("Lab stock request " + requestId + ": cannot release " + requested + " against " + remaining + " remaining.");
    }
}
