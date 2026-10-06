package com.adags.modules.clinical.domain;

import java.time.LocalDate;
import java.time.Period;

// 06-triage.md §2.2 originally named a single "child 3-12y" band. V78 split
// it into PRESCHOOL (3-5y) and SCHOOL_AGE (6-12y) so the seeded reference
// ranges can carry the tighter clinical bounds that differ between those
// sub-bands (HR 80-140 vs 70-120, RR 22-30 vs 18-25). CHILD stays in the
// enum for schema and history compatibility but forAge no longer returns it.
public enum AgeBand {
    NEONATE,
    INFANT,
    TODDLER,
    CHILD,
    PRESCHOOL,
    SCHOOL_AGE,
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
        if (years < 6) {
            return PRESCHOOL;
        }
        if (years < 13) {
            return SCHOOL_AGE;
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
