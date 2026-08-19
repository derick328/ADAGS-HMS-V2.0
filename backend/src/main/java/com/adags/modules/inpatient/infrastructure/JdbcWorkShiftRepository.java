package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.WorkShift;
import com.adags.modules.inpatient.domain.WorkShiftRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcWorkShiftRepository implements WorkShiftRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, code, name, department_id, start_time, end_time, crosses_midnight, days_of_week,
                   is_active, created_by, created_at
            FROM work_shift
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkShiftRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkShift insert(WorkShift workShift) {
        String sql = """
                INSERT INTO work_shift (code, name, department_id, start_time, end_time, crosses_midnight,
                                         days_of_week, is_active, created_by)
                VALUES (:code, :name, :departmentId, :startTime, :endTime, :crossesMidnight,
                        :daysOfWeek, :isActive, :createdBy)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", workShift.code())
                .addValue("name", workShift.name())
                .addValue("departmentId", workShift.departmentId(), Types.OTHER)
                .addValue("startTime", workShift.startTime())
                .addValue("endTime", workShift.endTime())
                .addValue("crossesMidnight", workShift.crossesMidnight())
                .addValue("daysOfWeek", workShift.daysOfWeek())
                .addValue("isActive", workShift.isActive())
                .addValue("createdBy", workShift.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new WorkShift(
                (UUID) rs.getObject("id"), workShift.code(), workShift.name(), workShift.departmentId(),
                workShift.startTime(), workShift.endTime(), workShift.crossesMidnight(), workShift.daysOfWeek(),
                workShift.isActive(), workShift.createdBy(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<WorkShift> findById(UUID id) {
        List<WorkShift> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<WorkShift> findActive() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE is_active = TRUE ORDER BY start_time",
                new MapSqlParameterSource(), this::mapRow);
    }

    private WorkShift mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WorkShift(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                (UUID) rs.getObject("department_id"),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getBoolean("crosses_midnight"),
                rs.getInt("days_of_week"),
                rs.getBoolean("is_active"),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
