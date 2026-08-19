package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 18-admin.md §4.1: "A staff record with a job title and no system role
// is valid — not everyone employed needs a login" — appUserId is
// nullable for exactly that reason. Deliberately minimal: job_title,
// license_number and the rest of nurse onboarding (§4.2) are a separate
// HR surface this item does not build; this is only what payroll needs
// to exist at all (employment_type to drive deductions, a basic salary
// to generate a payslip from).
public record Staff(
        UUID id,
        UUID appUserId,
        String fullName,
        EmploymentType employmentType,
        Money basicSalary,
        LocalDate employmentDate,
        boolean active,
        Instant createdAt
) {
}
