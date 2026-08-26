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
