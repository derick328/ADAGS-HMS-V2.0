package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

// 05-receptionist.md §1.1 ⚠1: "Build it as a separate projection queried by
// the receptionist workspace, not as a filtered version of the clinical
// query." Same direct-JdbcTemplate pattern as every other report in this
// module (e.g. ReceivablesOverviewService) — a read model spanning
// encounters/patients/billing tables that a facade-per-module boundary
// would only get in the way of.
//
// Provider display name: staff.full_name when the provider has a staff
// profile, else app_user.username (the bootstrap admin and any login
// without one). Invoice: the most recently created one for the encounter,
// since a walk-in typically has exactly one (the consultation charge) —
// picking "most recent" rather than erroring keeps this projection simple
// for the rare encounter that somehow has more than one.
@Service
public class VisitHistoryService {

    private static final String QUERY = """
            SELECT
                e.id AS encounter_id,
                e.created_at AS encounter_date,
                e.type AS encounter_type,
                e.state AS encounter_state,
                d.name AS department_name,
                COALESCE(s.full_name, u.username) AS attending_provider_name,
                i.id AS invoice_id,
                i.state AS invoice_state,
                i.outstanding_total_minor,
                i.currency_code
            FROM encounter e
            JOIN department d ON d.id = e.department_id
            LEFT JOIN app_user u ON u.id = e.assigned_provider_id
            LEFT JOIN staff s ON s.app_user_id = e.assigned_provider_id
            LEFT JOIN LATERAL (
                SELECT id, state, outstanding_total_minor, currency_code
                FROM invoice
                WHERE invoice.encounter_id = e.id
                ORDER BY created_at DESC
                LIMIT 1
            ) i ON true
            WHERE e.patient_id = :patientId
            ORDER BY e.created_at DESC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public VisitHistoryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VisitHistoryEntry> forPatient(UUID patientId) {
        return jdbcTemplate.query(QUERY, new MapSqlParameterSource("patientId", patientId), this::mapRow);
    }

    private VisitHistoryEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object invoiceId = rs.getObject("invoice_id");
        return new VisitHistoryEntry(
                (UUID) rs.getObject("encounter_id"),
                rs.getTimestamp("encounter_date").toInstant(),
                rs.getString("encounter_type"),
                rs.getString("encounter_state"),
                rs.getString("department_name"),
                rs.getString("attending_provider_name"),
                invoiceId == null ? null : (UUID) invoiceId,
                rs.getString("invoice_state"),
                rs.getLong("outstanding_total_minor"),
                rs.getString("currency_code"));
    }
}
