package com.adags.modules.reporting.application;

import java.util.UUID;

// 18-admin.md / 02-billing-architecture.md §13: revenue (cash collected +
// insurance billed) minus direct cost.
//
// ⚠ Direct cost here is pharmacy unit_cost only. Two cost components the
// brief names — consumables and attributable salaries — are not
// computed, because the data to attribute them to a department does not
// exist in the ledger: non-pharmacy stock (lab_stock_request) has no
// encounter/department link, and staff (V48) carries no department_id.
// contributionMinor is therefore an upper bound on true contribution,
// not the final number — the gap is surfaced explicitly (rather than
// silently treated as zero cost) so nobody mistakes this for a complete
// P&L line. Per the brief's own instruction: "the data was not
// captured, not that a report table should store it."
public record DepartmentContribution(
        java.util.UUID departmentId,
        String departmentName,
        long cashCollectedMinor,
        long insuranceBilledMinor,
        Long pharmacyCostMinor,
        Long contributionMinor,
        String currencyCode,
        boolean consumablesCostCaptured,
        boolean attributableSalariesCaptured
) {
}
