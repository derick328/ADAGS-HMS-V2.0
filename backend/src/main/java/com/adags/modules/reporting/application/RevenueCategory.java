package com.adags.modules.reporting.application;

import java.util.List;

// The one place a service_item becomes a revenue category. Both halves of
// every analytics query (billed and collected) and the top-items endpoint
// share CASE_SQL, so a line can never land in one bucket on one chart and
// a different bucket on another.
//
// service_item.category is NOT NULL with a five-value CHECK
// (CONSULTATION / LAB / PROCEDURE / PHARMACY / WARD), and service_item.type
// (nullable) is what distinguishes a theatre surgery from a day-case
// procedure inside category='PROCEDURE'. The CASE is therefore exhaustive
// and mutually exclusive: every invoice_line has exactly one
// service_item_id (NOT NULL) and every service_item falls in exactly one
// bucket, which is what makes the category chart sum to the period total
// with nothing counted twice.
//
// Radiology keys on service_item.type = 'RADIOLOGY' — the catalogue's own
// classification, 261 items, every one of them imaging (X-ray, MRI,
// ultrasound, contrast studies, scans) and all of them under
// category='PROCEDURE'. It is deliberately NOT keyed on
// billing_category='DIAGNOSTIC_EXAMINATIONS', which also covers 276
// laboratory items and would double-claim them, and certainly not on
// category='PROCEDURE', which would swallow all 858 surgeries.
//
// OPTICAL / DIALYSIS / OTHERS / OTHER_GENERAL_CHARGES and a null type under
// PROCEDURE still fall to OTHER rather than being dropped — a bucket an
// admin can see is honest; a silently discarded line is not.
//
// The bucket is derived at query time, never stored on the invoice line, so
// adding this category re-buckets history rather than losing it: charges
// that previously reported under OTHER now report under RADIOLOGY. No row
// changed, but a report run before and after this change will differ for
// those two buckets — which is the intended correction, not a discrepancy.
public enum RevenueCategory {
    CONSULTATION("Consultation / OPD"),
    SURGERY("Surgery"),
    THEATRE_PROCEDURE("Theatre procedures"),
    PHARMACY("Pharmacy / medication"),
    LABORATORY("Laboratory"),
    RADIOLOGY("Radiology / imaging"),
    INPATIENT("Inpatient / ward"),
    OTHER("Other services");

    public static final String CASE_SQL = """
            CASE
                WHEN si.category = 'CONSULTATION' THEN 'CONSULTATION'
                WHEN si.category = 'LAB'         THEN 'LABORATORY'
                WHEN si.category = 'PHARMACY'    THEN 'PHARMACY'
                WHEN si.category = 'WARD'        THEN 'INPATIENT'
                WHEN si.category = 'PROCEDURE' AND si.type = 'SURGERY'   THEN 'SURGERY'
                WHEN si.category = 'PROCEDURE' AND si.type = 'PROCEDURE' THEN 'THEATRE_PROCEDURE'
                WHEN si.category = 'PROCEDURE' AND si.type = 'RADIOLOGY' THEN 'RADIOLOGY'
                ELSE 'OTHER'
            END""";

    private final String displayName;

    RevenueCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Presentation order — highest-volume clinical categories first, OTHER last. */
    public static List<RevenueCategory> displayOrder() {
        return List.of(CONSULTATION, SURGERY, THEATRE_PROCEDURE, PHARMACY, LABORATORY, RADIOLOGY, INPATIENT, OTHER);
    }
}
