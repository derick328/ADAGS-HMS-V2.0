package com.adags.modules.insurance.domain;

// Deliberately a separate enum from billing.domain.ServiceItemCategory —
// root CLAUDE.md "Modules": no cross-module entity imports. Same string
// vocabulary as service_item.category (V15/V32) so a rule's category
// always matches a real service item's category; kept in sync by hand.
public enum ServiceCategory {
    CONSULTATION,
    LAB,
    PROCEDURE,
    PHARMACY,
    WARD
}
