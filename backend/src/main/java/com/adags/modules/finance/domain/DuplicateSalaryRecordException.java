package com.adags.modules.finance.domain;

import java.util.UUID;

// root CLAUDE.md "Concurrency": the partial unique index on
// salary_record (staff_id, month, year) is the real guard against
// generating one staff member's payslip twice for the same period —
// this is what the loser of a race (or a naive retry) gets.
public class DuplicateSalaryRecordException extends RuntimeException {

    public DuplicateSalaryRecordException(UUID staffId, int month, int year) {
        super("Staff " + staffId + " already has a salary record for " + year + "-" + month + ".");
    }
}
