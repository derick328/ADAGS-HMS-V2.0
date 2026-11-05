package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Bounded read model over source timestamps and the two explicit start events. */
@Service
public class TatAnalyticsService {
    private final NamedParameterJdbcTemplate jdbc;

    public TatAnalyticsService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String facilityTimezone(UUID facilityId) {
        var values = jdbc.query("SELECT timezone FROM facility WHERE id = :id",
                new MapSqlParameterSource("id", facilityId), (rs, n) -> rs.getString(1));
        if (values.isEmpty()) throw new NoSuchElementException("Facility not found: " + facilityId);
        return values.getFirst() == null || values.getFirst().isBlank() ? "Africa/Dar_es_Salaam" : values.getFirst();
    }

    public TatOverview overview(UUID facilityId, LocalDate from, LocalDate to) {
        var p = period(facilityId, from, to);
        var values = jdbc.queryForMap("""
                WITH visits AS (
                  SELECT e.id, e.created_at, e.state, a.id AS admission_id,
                    (SELECT max(ev.occurred_at) FROM encounter_event ev WHERE ev.encounter_id = e.id
                       AND ev.to_state IN ('CLOSED','DISCHARGED','REFERRED_OUT','DECEASED','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID','CANCELLED_IN_ERROR')) AS ended_at
                  FROM encounter e LEFT JOIN admission a ON a.encounter_id = e.id
                  WHERE e.facility_id = :facility AND e.created_at >= :from AND e.created_at < :to
                ), stages AS (
                  SELECT s.* FROM tat_stage_instance s JOIN visits v ON v.id = s.encounter_id
                ), per_visit AS (
                  SELECT encounter_id, sum(extract(epoch FROM upper(span) - lower(span))) AS wait_seconds
                  FROM (SELECT encounter_id, unnest(range_agg(tstzrange(ready_at, start_at, '[)'))) AS span
                        FROM stages WHERE start_at >= ready_at GROUP BY encounter_id) intervals
                  GROUP BY encounter_id
                )
                SELECT count(*) FILTER (WHERE v.ended_at IS NOT NULL) AS completed_encounters,
                  avg(extract(epoch FROM v.ended_at - v.created_at)) FILTER
                    (WHERE v.ended_at >= v.created_at AND v.admission_id IS NULL) AS avg_visit_seconds,
                  avg(w.wait_seconds) FILTER (WHERE v.admission_id IS NULL) AS avg_recorded_wait_seconds,
                  (SELECT avg(extract(epoch FROM start_at - ready_at)) FROM stages
                    WHERE workflow_type = 'TRIAGE' AND start_at >= ready_at) AS triage_wait_seconds,
                  (SELECT avg(extract(epoch FROM start_at - ready_at)) FROM stages
                    WHERE workflow_type = 'DOCTOR' AND start_at >= ready_at) AS doctor_wait_seconds,
                  (SELECT avg(extract(epoch FROM completed_at - ready_at)) FROM stages
                    WHERE workflow_type = 'LABORATORY' AND completed_at >= ready_at) AS lab_tat_seconds,
                  (SELECT avg(extract(epoch FROM completed_at - ready_at)) FROM stages
                    WHERE workflow_type = 'PHARMACY' AND completed_at >= ready_at) AS pharmacy_tat_seconds,
                  (SELECT count(DISTINCT s.encounter_id) FROM tat_stage_instance s
                    JOIN encounter active_encounter ON active_encounter.id = s.encounter_id
                    WHERE active_encounter.facility_id = :facility AND s.status = 'WAITING'
                      AND s.ready_at IS NOT NULL
                      AND active_encounter.state NOT IN ('CLOSED','DISCHARGED','REFERRED_OUT','DECEASED',
                        'LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID','CANCELLED_IN_ERROR')) AS currently_waiting
                FROM visits v LEFT JOIN per_visit w ON w.encounter_id = v.id
                """, p);
        return new TatOverview(number(values.get("completed_encounters")), number(values.get("currently_waiting")),
                numberOrNull(values.get("avg_visit_seconds")), numberOrNull(values.get("avg_recorded_wait_seconds")),
                numberOrNull(values.get("triage_wait_seconds")), numberOrNull(values.get("doctor_wait_seconds")),
                numberOrNull(values.get("lab_tat_seconds")), numberOrNull(values.get("pharmacy_tat_seconds")),
                facilityTimezone(facilityId));
    }

    public List<TatDepartment> departments(UUID facilityId, LocalDate from, LocalDate to, String workflowType) {
        var p = period(facilityId, from, to).addValue("workflow", workflowType);
        return jdbc.query("""
                SELECT s.workflow_type,
                  count(*) FILTER (WHERE s.completed_at IS NOT NULL) AS completed_services,
                  avg(extract(epoch FROM s.start_at - s.ready_at)) FILTER (WHERE s.start_at >= s.ready_at) AS avg_wait,
                  percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM s.start_at - s.ready_at))
                    FILTER (WHERE s.start_at >= s.ready_at) AS median_wait,
                  percentile_cont(0.9) WITHIN GROUP (ORDER BY extract(epoch FROM s.start_at - s.ready_at))
                    FILTER (WHERE s.start_at >= s.ready_at) AS p90_wait,
                  max(extract(epoch FROM s.start_at - s.ready_at)) FILTER (WHERE s.start_at >= s.ready_at) AS max_wait,
                  avg(extract(epoch FROM s.completed_at - s.start_at)) FILTER (WHERE s.completed_at >= s.start_at) AS avg_service,
                  avg(extract(epoch FROM s.completed_at - s.ready_at)) FILTER (WHERE s.completed_at >= s.ready_at) AS avg_tat
                FROM tat_stage_instance s JOIN encounter e ON e.id = s.encounter_id
                WHERE e.facility_id = :facility AND e.created_at >= :from AND e.created_at < :to
                  AND (:workflow IS NULL OR s.workflow_type = :workflow)
                GROUP BY s.workflow_type ORDER BY s.workflow_type
                """, p, (rs, n) -> new TatDepartment(rs.getString("workflow_type"), rs.getLong("completed_services"),
                nullableDouble(rs, "avg_wait"), nullableDouble(rs, "median_wait"), nullableDouble(rs, "p90_wait"),
                nullableDouble(rs, "max_wait"), nullableDouble(rs, "avg_service"), nullableDouble(rs, "avg_tat")));
    }

    public List<TatTrend> trend(UUID facilityId, LocalDate from, LocalDate to, String workflowType) {
        var p = period(facilityId, from, to).addValue("workflow", workflowType)
                .addValue("weekly", to.toEpochDay() - from.toEpochDay() > 30);
        return jdbc.query("""
                SELECT (CASE WHEN :weekly THEN date_trunc('week', s.source_at AT TIME ZONE f.timezone)
                             ELSE date_trunc('day', s.source_at AT TIME ZONE f.timezone) END)::date AS day,
                       s.workflow_type,
                  avg(extract(epoch FROM s.start_at - s.ready_at)) AS avg_wait_seconds,
                  count(*) AS sample_count
                FROM tat_stage_instance s JOIN encounter e ON e.id = s.encounter_id
                JOIN facility f ON f.id = e.facility_id
                WHERE e.facility_id = :facility AND e.created_at >= :from AND e.created_at < :to
                  AND s.start_at >= s.ready_at AND (:workflow IS NULL OR s.workflow_type = :workflow)
                GROUP BY day, s.workflow_type ORDER BY day, s.workflow_type LIMIT 400
                """, p, (rs, n) -> new TatTrend(rs.getDate("day").toLocalDate(), rs.getString("workflow_type"),
                rs.getDouble("avg_wait_seconds"), rs.getLong("sample_count")));
    }

    public List<TatWaiting> currentlyWaiting(UUID facilityId, String workflowType, int limit) {
        var p = new MapSqlParameterSource().addValue("facility", facilityId)
                .addValue("workflow", workflowType).addValue("limit", Math.max(1, Math.min(limit, 100)));
        return jdbc.query("""
                SELECT s.encounter_id, s.reference_id, s.workflow_type, s.ready_at, s.status,
                       p.hospital_no, trim(p.given_name || ' ' || p.family_name) AS patient_name,
                       extract(epoch FROM clock_timestamp() - s.ready_at) AS wait_seconds
                FROM tat_stage_instance s JOIN encounter e ON e.id = s.encounter_id
                JOIN patient p ON p.id = e.patient_id
                WHERE e.facility_id = :facility AND s.status = 'WAITING' AND s.ready_at IS NOT NULL
                  AND e.state NOT IN ('CLOSED','DISCHARGED','REFERRED_OUT','DECEASED','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID','CANCELLED_IN_ERROR')
                  AND (:workflow IS NULL OR s.workflow_type = :workflow)
                ORDER BY s.ready_at LIMIT :limit
                """, p, (rs, n) -> new TatWaiting((UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("reference_id"), rs.getString("workflow_type"),
                rs.getTimestamp("ready_at").toInstant(), rs.getString("status"),
                rs.getString("hospital_no"), rs.getString("patient_name"), rs.getLong("wait_seconds")));
    }

    public List<TatActivity> activity(UUID facilityId, LocalDate from, LocalDate to,
                                      String workflowType, String status, int page) {
        var p = period(facilityId, from, to).addValue("workflow", workflowType)
                .addValue("status", status).addValue("offset", Math.max(0, Math.min(page, 1000)) * 50);
        return jdbc.query("""
                SELECT s.*, p.hospital_no, trim(p.given_name || ' ' || p.family_name) AS patient_name
                FROM tat_stage_instance s JOIN encounter e ON e.id = s.encounter_id
                JOIN patient p ON p.id = e.patient_id
                WHERE e.facility_id = :facility AND e.created_at >= :from AND e.created_at < :to
                  AND (:workflow IS NULL OR s.workflow_type = :workflow)
                  AND (:status IS NULL OR s.status = :status)
                ORDER BY s.source_at DESC, s.reference_id LIMIT 50 OFFSET :offset
                """, p, (rs, n) -> new TatActivity((UUID) rs.getObject("encounter_id"),
                rs.getString("hospital_no"), rs.getString("patient_name"), stage(rs)));
    }

    public List<TatVisit> patientVisits(UUID facilityId, UUID patientId) {
        return jdbc.query("""
                SELECT e.id, e.created_at, e.state, e.type,
                  (SELECT max(ev.occurred_at) FROM encounter_event ev WHERE ev.encounter_id = e.id
                    AND ev.to_state IN ('CLOSED','DISCHARGED','REFERRED_OUT','DECEASED','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID','CANCELLED_IN_ERROR')) AS ended_at
                FROM encounter e WHERE e.facility_id = :facility AND e.patient_id = :patient
                ORDER BY e.created_at DESC LIMIT 50
                """, new MapSqlParameterSource().addValue("facility", facilityId).addValue("patient", patientId),
                (rs, n) -> visit(rs));
    }

    public TatJourney encounterJourney(UUID facilityId, UUID encounterId) {
        var p = new MapSqlParameterSource().addValue("facility", facilityId).addValue("encounter", encounterId);
        var visits = jdbc.query("""
                SELECT e.id, e.created_at, e.state, e.type,
                  (SELECT max(ev.occurred_at) FROM encounter_event ev WHERE ev.encounter_id = e.id
                    AND ev.to_state IN ('CLOSED','DISCHARGED','REFERRED_OUT','DECEASED','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID','CANCELLED_IN_ERROR')) AS ended_at
                FROM encounter e WHERE e.facility_id = :facility AND e.id = :encounter
                """, p, (rs, n) -> visit(rs));
        if (visits.isEmpty()) throw new NoSuchElementException("Encounter not found: " + encounterId);
        var stages = jdbc.query("""
                SELECT s.* FROM tat_stage_instance s JOIN encounter e ON e.id = s.encounter_id
                WHERE e.facility_id = :facility AND e.id = :encounter
                ORDER BY s.source_at, s.workflow_type, s.reference_id LIMIT 250
                """, p, (rs, n) -> stage(rs));
        var totalWait = jdbc.queryForObject("""
                SELECT sum(extract(epoch FROM upper(span) - lower(span)))
                FROM (SELECT unnest(range_agg(tstzrange(ready_at, start_at, '[)'))) AS span
                      FROM tat_stage_instance WHERE encounter_id = :encounter AND start_at >= ready_at) intervals
                """, p, Double.class);
        return new TatJourney(visits.getFirst(), stages, totalWait, facilityTimezone(facilityId));
    }

    private MapSqlParameterSource period(UUID facilityId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(366)))
            throw new IllegalArgumentException("Choose a valid date range of at most 366 days.");
        var zone = ZoneId.of(facilityTimezone(facilityId));
        return new MapSqlParameterSource().addValue("facility", facilityId)
                .addValue("from", from.atStartOfDay(zone).toInstant())
                .addValue("to", to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private TatVisit visit(ResultSet rs) throws SQLException {
        Instant start = instant(rs, "created_at"), end = instant(rs, "ended_at");
        return new TatVisit((UUID) rs.getObject("id"), start, end, rs.getString("state"),
                rs.getString("type"), seconds(start, end), end != null && end.isBefore(start));
    }

    private TatStage stage(ResultSet rs) throws SQLException {
        Instant ready = instant(rs, "ready_at"), start = instant(rs, "start_at"), end = instant(rs, "completed_at");
        boolean anomaly = (ready != null && start != null && start.isBefore(ready))
                || (start != null && end != null && end.isBefore(start))
                || (ready != null && end != null && end.isBefore(ready));
        return new TatStage((UUID) rs.getObject("reference_id"), rs.getString("workflow_type"), rs.getString("status"),
                instant(rs, "source_at"), ready, start, end, instant(rs, "received_at"),
                instant(rs, "entered_at"), instant(rs, "verified_at"),
                seconds(ready, start), seconds(start, end), seconds(ready, end), anomaly);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static Long seconds(Instant start, Instant end) {
        return start == null || end == null || end.isBefore(start) ? null : Duration.between(start, end).toSeconds();
    }
    private static long number(Object value) { return value == null ? 0 : ((Number) value).longValue(); }
    private static Double numberOrNull(Object value) { return value == null ? null : ((Number) value).doubleValue(); }
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    public record TatOverview(long completedEncounters, long currentlyWaiting, Double averageVisitSeconds,
                              Double averageRecordedWaitingSeconds, Double triageWaitingSeconds,
                              Double doctorWaitingSeconds, Double laboratoryTatSeconds,
                              Double pharmacyTatSeconds, String timezone) {}
    public record TatDepartment(String workflowType, long completedServices, Double averageWaitingSeconds,
                                Double medianWaitingSeconds, Double p90WaitingSeconds, Double longestWaitingSeconds,
                                Double averageServiceSeconds, Double averageTatSeconds) {}
    public record TatTrend(LocalDate day, String workflowType, double averageWaitingSeconds, long sampleCount) {}
    public record TatWaiting(UUID encounterId, UUID referenceId, String workflowType, Instant readyAt,
                             String status, String hospitalNo, String patientName, long currentWaitingSeconds) {}
    public record TatVisit(UUID encounterId, Instant arrivedAt, Instant endedAt, String state,
                           String type, Long visitSeconds, boolean timestampAnomaly) {}
    public record TatStage(UUID referenceId, String workflowType, String status, Instant sourceAt,
                           Instant readyAt, Instant startedAt, Instant completedAt, Instant receivedAt,
                           Instant enteredAt, Instant verifiedAt, Long waitingSeconds, Long serviceSeconds,
                           Long totalTatSeconds, boolean timestampAnomaly) {}
    public record TatJourney(TatVisit visit, List<TatStage> stages, Double totalRecordedWaitingSeconds,
                             String timezone) {}
    public record TatActivity(UUID encounterId, String hospitalNo, String patientName, TatStage stage) {}
}
