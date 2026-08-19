package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ReceivablesOverviewService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReceivablesOverviewService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReceivablesOverview generate(UUID facilityId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("facilityId", facilityId);

        Map<String, Object> postDischarge = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS invoice_count, COALESCE(SUM(outstanding_total_minor), 0) AS total_minor
                FROM invoice
                WHERE state = 'RECEIVABLE_POST_DISCHARGE' AND facility_id = :facilityId
                """, params);

        // ISSUED / PATIENT_PORTION_SETTLED: the encounter is still open,
        // the billing gate still governs new charges against it — this is
        // not yet a receivable to chase.
        Map<String, Object> active = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS invoice_count, COALESCE(SUM(outstanding_total_minor), 0) AS total_minor
                FROM invoice
                WHERE state IN ('ISSUED', 'PATIENT_PORTION_SETTLED')
                  AND outstanding_total_minor > 0
                  AND facility_id = :facilityId
                """, params);

        return new ReceivablesOverview(
                ((Number) postDischarge.get("total_minor")).longValue(),
                ((Number) postDischarge.get("invoice_count")).intValue(),
                ((Number) active.get("total_minor")).longValue(),
                ((Number) active.get("invoice_count")).intValue(),
                "TZS");
    }
}
