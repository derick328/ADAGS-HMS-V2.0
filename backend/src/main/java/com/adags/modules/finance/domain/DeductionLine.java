package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;

import java.util.UUID;

// 18-admin.md §9.3: "deductions stores the rule_id applied, so a
// historical payslip is reconstructable even after rates change." One
// line per DeductionRule applied — never the employer-paid ones (§9.1).
public record DeductionLine(String code, Money amount, UUID ruleId) {
}
