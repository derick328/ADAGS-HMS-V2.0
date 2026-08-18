package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.GenderRestriction;
import com.adags.modules.inpatient.domain.Ward;
import com.adags.modules.inpatient.domain.WardClass;
import com.adags.modules.inpatient.domain.WardRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcWardRepository implements WardRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, facility_id, department_id, code, name, ward_class, gender_restriction,
                   daily_rate_minor, currency_code, active, created_at
            FROM ward
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Ward> findById(UUID id) {
        List<Ward> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Ward> findByFacilityId(UUID facilityId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE facility_id = :facilityId AND active ORDER BY name",
                new MapSqlParameterSource("facilityId", facilityId), this::mapRow);
    }

    @Override
    public Ward insert(Ward ward) {
        String sql = """
                INSERT INTO ward (facility_id, department_id, code, name, ward_class, gender_restriction,
                                   daily_rate_minor, currency_code)
                VALUES (:facilityId, :departmentId, :code, :name, :wardClass, :genderRestriction,
                        :dailyRateMinor, :currencyCode)
                RETURNING id, active, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("facilityId", ward.facilityId())
                .addValue("departmentId", ward.departmentId())
                .addValue("code", ward.code())
                .addValue("name", ward.name())
                .addValue("wardClass", ward.wardClass().name())
                .addValue("genderRestriction",
                        ward.genderRestriction() == null ? null : ward.genderRestriction().name(), Types.VARCHAR)
                .addValue("dailyRateMinor", ward.dailyRate().amountMinor())
                .addValue("currencyCode", ward.dailyRate().currencyCode());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Ward(
                (UUID) rs.getObject("id"), ward.facilityId(), ward.departmentId(), ward.code(), ward.name(),
                ward.wardClass(), ward.genderRestriction(), ward.dailyRate(),
                rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant()));
    }

    private Ward mapRow(ResultSet rs, int rowNum) throws SQLException {
        String genderRestriction = rs.getString("gender_restriction");
        return new Ward(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("facility_id"),
                (UUID) rs.getObject("department_id"),
                rs.getString("code"),
                rs.getString("name"),
                WardClass.valueOf(rs.getString("ward_class")),
                genderRestriction == null ? null : GenderRestriction.valueOf(genderRestriction),
                new Money(rs.getLong("daily_rate_minor"), rs.getString("currency_code")),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant());
    }
}
