package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabResultParameterValue;
import com.adags.modules.laboratory.domain.LabResultParameterValueRepository;
import com.adags.modules.laboratory.domain.ResultFlag;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcLabResultParameterValueRepository implements LabResultParameterValueRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabResultParameterValueRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabResultParameterValue insert(LabResultParameterValue value) {
        String sql = """
                INSERT INTO lab_result_parameter_value (result_id, parameter_id, result_value, flag,
                                                          flag_overridden, override_reason, applied_range_id)
                VALUES (:resultId, :parameterId, :resultValue, :flag, :flagOverridden, :overrideReason, :appliedRangeId)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("resultId", value.resultId())
                .addValue("parameterId", value.parameterId())
                .addValue("resultValue", value.resultValue())
                .addValue("flag", value.flag().name())
                .addValue("flagOverridden", value.flagOverridden())
                .addValue("overrideReason", value.overrideReason())
                .addValue("appliedRangeId", value.appliedRangeId(), Types.OTHER);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabResultParameterValue(
                (UUID) rs.getObject("id"), value.resultId(), value.parameterId(), value.resultValue(), value.flag(),
                value.flagOverridden(), value.overrideReason(), value.appliedRangeId(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<LabResultParameterValue> findByResultId(UUID resultId) {
        String sql = """
                SELECT id, result_id, parameter_id, result_value, flag, flag_overridden, override_reason,
                       applied_range_id, created_at
                FROM lab_result_parameter_value
                WHERE result_id = :resultId
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("resultId", resultId), this::mapRow);
    }

    private LabResultParameterValue mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object appliedRangeId = rs.getObject("applied_range_id");
        return new LabResultParameterValue(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("result_id"),
                (UUID) rs.getObject("parameter_id"),
                rs.getDouble("result_value"),
                ResultFlag.valueOf(rs.getString("flag")),
                rs.getBoolean("flag_overridden"),
                rs.getString("override_reason"),
                appliedRangeId == null ? null : (UUID) appliedRangeId,
                rs.getTimestamp("created_at").toInstant());
    }
}
