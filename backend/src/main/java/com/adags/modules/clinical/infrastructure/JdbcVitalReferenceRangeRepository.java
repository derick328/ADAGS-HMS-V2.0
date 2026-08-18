package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.domain.AgeBand;
import com.adags.modules.clinical.domain.Sex;
import com.adags.modules.clinical.domain.VitalReferenceRange;
import com.adags.modules.clinical.domain.VitalReferenceRangeRepository;
import com.adags.modules.clinical.domain.VitalType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcVitalReferenceRangeRepository implements VitalReferenceRangeRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, vital_type, age_band, sex, implausible_low, implausible_high,
                   abnormal_low, abnormal_high, effective_from, effective_to
            FROM vital_reference_range
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcVitalReferenceRangeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<VitalReferenceRange> findCurrent(VitalType vitalType, AgeBand ageBand, Sex sex) {
        String sql = SELECT_COLUMNS + """
                 WHERE vital_type = :vitalType AND age_band = :ageBand AND effective_to IS NULL
                   AND sex IN (:sex, 'ANY')
                 ORDER BY CASE WHEN sex = :sex THEN 0 ELSE 1 END
                 LIMIT 1
                """;
        List<VitalReferenceRange> results = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("vitalType", vitalType.name())
                .addValue("ageBand", ageBand.name())
                .addValue("sex", sex.name()), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<VitalReferenceRange> findUnconfigured() {
        String sql = SELECT_COLUMNS + """
                 WHERE effective_to IS NULL AND implausible_low IS NULL AND implausible_high IS NULL
                 ORDER BY vital_type, age_band, sex
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), this::mapRow);
    }

    private VitalReferenceRange mapRow(ResultSet rs, int rowNum) throws SQLException {
        Double implausibleLow = getNullableDouble(rs, "implausible_low");
        Double implausibleHigh = getNullableDouble(rs, "implausible_high");
        Double abnormalLow = getNullableDouble(rs, "abnormal_low");
        Double abnormalHigh = getNullableDouble(rs, "abnormal_high");
        return new VitalReferenceRange(
                (UUID) rs.getObject("id"),
                VitalType.valueOf(rs.getString("vital_type")),
                AgeBand.valueOf(rs.getString("age_band")),
                Sex.valueOf(rs.getString("sex")),
                implausibleLow, implausibleHigh, abnormalLow, abnormalHigh,
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class));
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
