package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.Sex;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcLabReferenceRangeRepository implements LabReferenceRangeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabReferenceRangeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabReferenceRange insert(LabReferenceRange range) {
        String sql = """
                INSERT INTO lab_reference_range (parameter_id, age_min_months, age_max_months, sex,
                                                   implausible_low, implausible_high,
                                                   normal_low, normal_high, critical_low, critical_high, created_by)
                VALUES (:parameterId, :ageMinMonths, :ageMaxMonths, :sex,
                        :implausibleLow, :implausibleHigh,
                        :normalLow, :normalHigh, :criticalLow, :criticalHigh, :createdBy)
                RETURNING id, effective_from, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("parameterId", range.parameterId())
                .addValue("ageMinMonths", range.ageMinMonths())
                .addValue("ageMaxMonths", range.ageMaxMonths())
                .addValue("sex", range.sex().name())
                .addValue("implausibleLow", range.implausibleLow())
                .addValue("implausibleHigh", range.implausibleHigh())
                .addValue("normalLow", range.normalLow())
                .addValue("normalHigh", range.normalHigh())
                .addValue("criticalLow", range.criticalLow())
                .addValue("criticalHigh", range.criticalHigh())
                .addValue("createdBy", range.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabReferenceRange(
                (UUID) rs.getObject("id"), range.parameterId(), range.ageMinMonths(), range.ageMaxMonths(),
                range.sex(), range.implausibleLow(), range.implausibleHigh(), range.normalLow(), range.normalHigh(),
                range.criticalLow(), range.criticalHigh(),
                rs.getDate("effective_from").toLocalDate(), null, range.createdBy(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<LabReferenceRange> findCurrentByParameterId(UUID parameterId) {
        String sql = """
                SELECT id, parameter_id, age_min_months, age_max_months, sex,
                       implausible_low, implausible_high, normal_low, normal_high, critical_low, critical_high,
                       effective_from, effective_to, created_by, created_at
                FROM lab_reference_range
                WHERE parameter_id = :parameterId AND effective_to IS NULL
                ORDER BY age_min_months
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("parameterId", parameterId), this::mapRow);
    }

    private LabReferenceRange mapRow(ResultSet rs, int rowNum) throws SQLException {
        java.sql.Date effectiveTo = rs.getDate("effective_to");
        Object ageMaxMonths = rs.getObject("age_max_months");
        return new LabReferenceRange(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("parameter_id"),
                rs.getInt("age_min_months"),
                ageMaxMonths == null ? null : ((Number) ageMaxMonths).intValue(),
                Sex.valueOf(rs.getString("sex")),
                toDouble(rs.getObject("implausible_low")),
                toDouble(rs.getObject("implausible_high")),
                toDouble(rs.getObject("normal_low")),
                toDouble(rs.getObject("normal_high")),
                toDouble(rs.getObject("critical_low")),
                toDouble(rs.getObject("critical_high")),
                rs.getDate("effective_from").toLocalDate(),
                effectiveTo == null ? null : effectiveTo.toLocalDate(),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
