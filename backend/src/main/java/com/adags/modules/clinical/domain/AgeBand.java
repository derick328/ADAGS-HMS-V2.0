package com.adags.modules.clinical.domain;

import java.time.LocalDate;
import java.time.Period;

// 06-triage.md §2.2: "neonate 0-1m, infant 1-12m, toddler 1-3y, child
// 3-12y, adolescent 12-18y, adult, elderly 65+."
public enum AgeBand {
    NEONATE,
    INFANT,
    TODDLER,
    CHILD,
    ADOLESCENT,
    ADULT,
    ELDERLY;

    public static AgeBand forAge(LocalDate dateOfBirth, LocalDate asOf) {
        Period age = Period.between(dateOfBirth, asOf);
        int totalMonths = age.getYears() * 12 + age.getMonths();
        int years = age.getYears();

        if (totalMonths < 1) {
            return NEONATE;
        }
        if (totalMonths < 12) {
            return INFANT;
        }
        if (years < 3) {
            return TODDLER;
        }
        if (years < 12) {
            return CHILD;
        }
        if (years < 18) {
            return ADOLESCENT;
        }
        if (years < 65) {
            return ADULT;
        }
        return ELDERLY;
    }
}
