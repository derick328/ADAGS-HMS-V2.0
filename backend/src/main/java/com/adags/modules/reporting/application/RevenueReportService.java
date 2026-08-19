package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

// 02-billing-architecture.md §13. A pure read model over the existing
// ledger — no new financial table. Every module in this package queries
// the schema directly (never another module's Java repository/entity),
// which is what keeps it outside ModuleBoundaryTest's cross-module rule
// entirely rather than needing a Facade exemption.
@Service
public class RevenueReportService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RevenueReportService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RevenueReport generate(LocalDate from, LocalDate to, UUID facilityId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from).addValue("to", to).addValue("facilityId", facilityId);

        // Settled patient-side payments in the period. payment.method can
        // never be INSURANCE (V17: "coverage is a receivable, not cash
        // received") — every settled allocation here is, by construction,
        // money actually collected, whatever the specific method.
        long collectedCashMinor = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(pa.amount_minor - pa.refunded_minor), 0)
                FROM payment_allocation pa
                JOIN payment p ON p.id = pa.payment_id
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                JOIN invoice i ON i.id = il.invoice_id
                WHERE p.status = 'SETTLED'
                  AND p.received_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                """, params, Long.class);

        // "Billed", not "collected" — the accrual event is the claim
        // being raised, not any later remittance. One claim per invoice
        // (resubmission transitions the existing row, never inserts a
        // second), so summing per claim row cannot double-count.
        long insuranceBilledMinor = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(i.insurance_portion_total_minor), 0)
                FROM insurance_claim c
                JOIN invoice i ON i.id = c.invoice_id
                WHERE c.created_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                """, params, Long.class);

        // Snapshot, not period-bound: "accrued and unpaid" as of now.
        // EMERGENCY_BYPASS-authorized lines whose patient portion is still
        // not fully settled — the same settled-allocation netting
        // BillingGateImpl.rejection() uses to compute what's still owed.
        long emergencyBypassDebtMinor = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(il.patient_portion_minor - COALESCE(settled.settled_minor, 0)), 0)
                FROM invoice_line il
                JOIN service_authorization sa ON sa.id = il.authorization_id
                JOIN invoice i ON i.id = il.invoice_id
                LEFT JOIN (
                    SELECT pa2.invoice_line_id, SUM(pa2.amount_minor - pa2.refunded_minor) AS settled_minor
                    FROM payment_allocation pa2
                    JOIN payment p2 ON p2.id = pa2.payment_id
                    WHERE p2.status = 'SETTLED'
                    GROUP BY pa2.invoice_line_id
                ) settled ON settled.invoice_line_id = il.id
                WHERE sa.authorization_type = 'EMERGENCY_BYPASS'
                  AND i.state != 'VOID'
                  AND i.facility_id = :facilityId
                  AND il.patient_portion_minor - COALESCE(settled.settled_minor, 0) > 0
                """, params, Long.class);

        return new RevenueReport(from, to, collectedCashMinor, insuranceBilledMinor, emergencyBypassDebtMinor,
                0L, "TZS");
    }
}
