package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.ShiftAssignment;
import com.adags.modules.inpatient.domain.ShiftAssignmentRepository;
import com.adags.modules.inpatient.domain.ShiftAssignmentStatus;
import com.adags.modules.inpatient.domain.ShiftRoleContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcShiftAssignmentRepository implements ShiftAssignmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, user_id, work_shift_id, shift_date, ward_id, department_id, role_context, status,
                   assigned_by, assigned_at, swapped_with_user_id, swap_reason
            FROM shift_assignment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcShiftAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ShiftAssignment insert(ShiftAssignment assignment) {
        String sql = """
                INSERT INTO shift_assignment (user_id, work_shift_id, shift_date, ward_id, department_id,
                                               role_context, status, assigned_by)
                VALUES (:userId, :workShiftId, :shiftDate, :wardId, :departmentId,
                        :roleContext, :status, :assignedBy)
                RETURNING id, assigned_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", assignment.userId())
                .addValue("workShiftId", assignment.workShiftId())
                .addValue("shiftDate", Date.valueOf(assignment.shiftDate()))
                .addValue("wardId", assignment.wardId(), Types.OTHER)
                .addValue("departmentId", assignment.departmentId(), Types.OTHER)
                .addValue("roleContext", assignment.roleContext().name())
                .addValue("status", assignment.status().name())
                .addValue("assignedBy", assignment.assignedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ShiftAssignment(
                (UUID) rs.getObject("id"), assignment.userId(), assignment.workShiftId(), assignment.shiftDate(),
                assignment.wardId(), assignment.departmentId(), assignment.roleContext(), assignment.status(),
                assignment.assignedBy(), rs.getTimestamp("assigned_at").toInstant(),
                assignment.swappedWithUserId(), assignment.swapReason()));
    }

    @Override
    public Optional<ShiftAssignment> findById(UUID id) {
        List<ShiftAssignment> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<ShiftAssignment> findByWardIdAndShiftDate(UUID wardId, LocalDate shiftDate) {
        String sql = SELECT_COLUMNS + " WHERE ward_id = :wardId AND shift_date = :shiftDate ORDER BY assigned_at";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("wardId", wardId)
                .addValue("shiftDate", Date.valueOf(shiftDate));
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    @Override
    public List<ShiftAssignment> findByUserIdAndShiftDate(UUID userId, LocalDate shiftDate) {
        String sql = SELECT_COLUMNS + " WHERE user_id = :userId AND shift_date = :shiftDate ORDER BY assigned_at";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("shiftDate", Date.valueOf(shiftDate));
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    private ShiftAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp assignedAt = rs.getTimestamp("assigned_at");
        return new ShiftAssignment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                (UUID) rs.getObject("work_shift_id"),
                rs.getDate("shift_date").toLocalDate(),
                (UUID) rs.getObject("ward_id"),
                (UUID) rs.getObject("department_id"),
                ShiftRoleContext.valueOf(rs.getString("role_context")),
                ShiftAssignmentStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("assigned_by"),
                assignedAt == null ? null : assignedAt.toInstant(),
                (UUID) rs.getObject("swapped_with_user_id"),
                rs.getString("swap_reason"));
    }
}
