package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 02-billing-architecture.md §13: "Claims are billed, not collected."
// Grouped by whatever state values are actually present in the period —
// not hardcoded to DRAFT/SUBMITTED/APPROVED/REJECTED, so RESUBMITTED and
// SETTLED (ClaimState — Phase 7 item 3 part 3) show up too rather than
// being silently absorbed into an adjacent bucket.
@Service
public class ClaimsReportService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ClaimsReportService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ClaimsSummaryLine> summarize(LocalDate from, LocalDate to, UUID facilityId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from).addValue("to", to).addValue("facilityId", facilityId);
        return jdbcTemplate.query("""
                SELECT c.state AS state, COUNT(*) AS cnt, COALESCE(SUM(i.insurance_portion_total_minor), 0) AS total_minor
                FROM insurance_claim c
                JOIN invoice i ON i.id = c.invoice_id
                WHERE c.created_at::date BETWEEN :from AND :to AND i.facility_id = :facilityId
                GROUP BY c.state
                ORDER BY c.state
                """, params, (rs, rowNum) -> new ClaimsSummaryLine(
                        rs.getString("state"), rs.getInt("cnt"), rs.getLong("total_minor"), "TZS"));
    }
}
