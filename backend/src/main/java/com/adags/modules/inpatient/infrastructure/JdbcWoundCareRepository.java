package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.WoundCare;
import com.adags.modules.inpatient.domain.WoundCareRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcWoundCareRepository implements WoundCareRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, admission_id, appearance, dressing_changed, dressing_type, signs_of_infection,
                   infection_description, remarks, recorded_by, recorded_at
            FROM wound_care
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWoundCareRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WoundCare insert(WoundCare woundCare) {
        String sql = """
                INSERT INTO wound_care (admission_id, appearance, dressing_changed, dressing_type,
                                         signs_of_infection, infection_description, remarks, recorded_by)
                VALUES (:admissionId, :appearance, :dressingChanged, :dressingType,
                        :signsOfInfection, :infectionDescription, :remarks, :recordedBy)
                RETURNING id, recorded_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("admissionId", woundCare.admissionId())
                .addValue("appearance", woundCare.appearance())
                .addValue("dressingChanged", woundCare.dressingChanged())
                .addValue("dressingType", woundCare.dressingType(), Types.VARCHAR)
                .addValue("signsOfInfection", woundCare.signsOfInfection())
                .addValue("infectionDescription", woundCare.infectionDescription(), Types.VARCHAR)
                .addValue("remarks", woundCare.remarks(), Types.VARCHAR)
                .addValue("recordedBy", woundCare.recordedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new WoundCare(
                (UUID) rs.getObject("id"), woundCare.admissionId(), woundCare.appearance(), woundCare.dressingChanged(),
                woundCare.dressingType(), woundCare.signsOfInfection(), woundCare.infectionDescription(),
                woundCare.remarks(), woundCare.recordedBy(), rs.getTimestamp("recorded_at").toInstant()));
    }

    @Override
    public List<WoundCare> findByAdmissionId(UUID admissionId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE admission_id = :admissionId ORDER BY recorded_at DESC",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    private WoundCare mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WoundCare(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("admission_id"),
                rs.getString("appearance"),
                rs.getBoolean("dressing_changed"),
                rs.getString("dressing_type"),
                rs.getBoolean("signs_of_infection"),
                rs.getString("infection_description"),
                rs.getString("remarks"),
                (UUID) rs.getObject("recorded_by"),
                rs.getTimestamp("recorded_at").toInstant());
    }
}
