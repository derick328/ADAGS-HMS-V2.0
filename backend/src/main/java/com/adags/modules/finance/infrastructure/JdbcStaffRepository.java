package com.adags.modules.finance.infrastructure;

import com.adags.modules.finance.domain.EmploymentType;
import com.adags.modules.finance.domain.Staff;
import com.adags.modules.finance.domain.StaffRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStaffRepository implements StaffRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, app_user_id, full_name, employment_type, basic_salary_minor, employment_date, active, created_at
            FROM staff
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStaffRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Staff insert(Staff staff) {
        String sql = """
                INSERT INTO staff (app_user_id, full_name, employment_type, basic_salary_minor, employment_date, active)
                VALUES (:appUserId, :fullName, :employmentType, :basicSalaryMinor, :employmentDate, :active)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("appUserId", staff.appUserId())
                .addValue("fullName", staff.fullName())
                .addValue("employmentType", staff.employmentType().name())
                .addValue("basicSalaryMinor", staff.basicSalary() == null ? null : staff.basicSalary().amountMinor())
                .addValue("employmentDate", staff.employmentDate())
                .addValue("active", staff.active());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Staff(
                (UUID) rs.getObject("id"), staff.appUserId(), staff.fullName(), staff.employmentType(),
                staff.basicSalary(), staff.employmentDate(), staff.active(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<Staff> findById(UUID id) {
        List<Staff> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Staff> findAllActive() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE active = TRUE ORDER BY full_name",
                new MapSqlParameterSource(), this::mapRow);
    }

    private Staff mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object appUserId = rs.getObject("app_user_id");
        Object basicSalaryMinor = rs.getObject("basic_salary_minor");
        Date employmentDate = rs.getDate("employment_date");
        return new Staff(
                (UUID) rs.getObject("id"),
                appUserId == null ? null : (UUID) appUserId,
                rs.getString("full_name"),
                EmploymentType.valueOf(rs.getString("employment_type")),
                basicSalaryMinor == null ? null : new Money(((Number) basicSalaryMinor).longValue(), "TZS"),
                employmentDate == null ? null : employmentDate.toLocalDate(),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant());
    }
}
