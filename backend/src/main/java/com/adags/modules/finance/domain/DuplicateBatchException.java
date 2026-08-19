package com.adags.modules.finance.domain;

// root CLAUDE.md "Concurrency": UNIQUE (batch_type, month, year) is the
// real guard against generating one month's payroll twice — "running
// twice for one month must not pay twice" (18-admin.md §5.1).
public class DuplicateBatchException extends RuntimeException {

    public DuplicateBatchException(String batchType, int month, int year) {
        super("A " + batchType + " batch for " + year + "-" + month + " already exists.");
    }
}
