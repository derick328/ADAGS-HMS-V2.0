package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.NursingAssignment;
import com.adags.modules.inpatient.domain.NursingAssignmentRepository;
import com.adags.modules.inpatient.domain.Shift;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNursingAssignmentRepository implements NursingAssignmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, admission_id, nurse_id, shift, assignment_date, assigned_by, created_at
            FROM nursing_assignment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcNursingAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<NursingAssignment> findByAdmissionId(UUID admissionId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE admission_id = :admissionId ORDER BY assignment_date DESC, shift",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    @Override
    public List<NursingAssignment> findByNurseId(UUID nurseId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE nurse_id = :nurseId ORDER BY assignment_date DESC, shift",
                new MapSqlParameterSource("nurseId", nurseId), this::mapRow);
    }

    @Override
    public boolean existsForNurseAndAdmission(UUID admissionId, UUID nurseId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM nursing_assignment WHERE admission_id = :admissionId AND nurse_id = :nurseId)",
                new MapSqlParameterSource()
                        .addValue("admissionId", admissionId)
                        .addValue("nurseId", nurseId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<NursingAssignment> findForShift(UUID admissionId, LocalDate date, Shift shift) {
        List<NursingAssignment> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE admission_id = :admissionId AND assignment_date = :date AND shift = :shift",
                new MapSqlParameterSource()
                        .addValue("admissionId", admissionId)
                        .addValue("date", Date.valueOf(date))
                        .addValue("shift", shift.name()),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public NursingAssignment insert(NursingAssignment assignment) {
        String sql = """
                INSERT INTO nursing_assignment (admission_id, nurse_id, shift, assignment_date, assigned_by)
                VALUES (:admissionId, :nurseId, :shift, :assignmentDate, :assignedBy)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("admissionId", assignment.admissionId())
                .addValue("nurseId", assignment.nurseId())
                .addValue("shift", assignment.shift().name())
                .addValue("assignmentDate", Date.valueOf(assignment.assignmentDate()))
                .addValue("assignedBy", assignment.assignedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new NursingAssignment(
                (UUID) rs.getObject("id"), assignment.admissionId(), assignment.nurseId(), assignment.shift(),
                assignment.assignmentDate(), assignment.assignedBy(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<NurseWorkload> countActiveAdmissionsPerNurse(UUID facilityId) {
        // LEFT JOIN so every nurse in this facility appears — even the ones
        // with zero current patients (doctor sees them as candidates too).
        // A nurse belongs to this facility when they hold WARD_NURSE or NURSE
        // via a role_assignment that's currently valid (valid_to null or in
        // the future) at GLOBAL or FACILITY scope on this facility. "Active"
        // admission = actual_departure_at IS NULL; DISTINCT admission_id so a
        // nurse who covered three shifts for the same patient still counts as
        // one patient. Ordered by workload asc then username so the picker
        // reads "least loaded first".
        String sql = """
                SELECT nurse.id AS nurse_id,
                       nurse.username AS username,
                       COALESCE(load.active_patients, 0) AS active_patients
                  FROM app_user nurse
                  JOIN role_assignment ra ON ra.user_id = nurse.id
                  JOIN role r ON r.id = ra.role_id
                  LEFT JOIN (
                      SELECT na.nurse_id,
                             COUNT(DISTINCT na.admission_id) AS active_patients
                        FROM nursing_assignment na
                        JOIN admission adm ON adm.id = na.admission_id
                       WHERE adm.actual_departure_at IS NULL
                         AND adm.facility_id = :facilityId
                       GROUP BY na.nurse_id
                  ) load ON load.nurse_id = nurse.id
                 WHERE nurse.status = 'ACTIVE'
                   AND r.code IN ('WARD_NURSE', 'NURSE')
                   AND (ra.valid_to IS NULL OR ra.valid_to > now())
                   AND (ra.valid_from IS NULL OR ra.valid_from <= now())
                   AND (
                       ra.scope_type = 'GLOBAL'
                       OR (ra.scope_type = 'FACILITY' AND ra.scope_id = :facilityId)
                   )
                 GROUP BY nurse.id, nurse.username, load.active_patients
                 ORDER BY active_patients ASC, nurse.username ASC
                """;
        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("facilityId", facilityId),
                (rs, rowNum) -> new NurseWorkload(
                        (UUID) rs.getObject("nurse_id"),
                        rs.getString("username"),
                        rs.getInt("active_patients")));
    }

    private NursingAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NursingAssignment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("admission_id"),
                (UUID) rs.getObject("nurse_id"),
                Shift.valueOf(rs.getString("shift")),
                rs.getDate("assignment_date").toLocalDate(),
                (UUID) rs.getObject("assigned_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
