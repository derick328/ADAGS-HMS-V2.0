package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcLabTestParameterRepository implements LabTestParameterRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabTestParameterRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabTestParameter insert(LabTestParameter parameter) {
        String sql = """
                INSERT INTO lab_test_parameter (test_id, code, name, unit, sort_order, method, loinc_code)
                VALUES (:testId, :code, :name, :unit, :sortOrder, :method, :loincCode)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("testId", parameter.testId())
                .addValue("code", parameter.code())
                .addValue("name", parameter.name())
                .addValue("unit", parameter.unit())
                .addValue("sortOrder", parameter.sortOrder())
                .addValue("method", parameter.method())
                .addValue("loincCode", parameter.loincCode());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabTestParameter(
                (UUID) rs.getObject("id"), parameter.testId(), parameter.code(), parameter.name(), parameter.unit(),
                parameter.sortOrder(), parameter.method(), parameter.loincCode(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<LabTestParameter> findByTestId(UUID testId) {
        String sql = """
                SELECT id, test_id, code, name, unit, sort_order, method, loinc_code, created_at
                FROM lab_test_parameter
                WHERE test_id = :testId
                ORDER BY sort_order
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("testId", testId), this::mapRow);
    }

    private LabTestParameter mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LabTestParameter(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("test_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("unit"),
                rs.getInt("sort_order"),
                rs.getString("method"),
                rs.getString("loinc_code"),
                rs.getTimestamp("created_at").toInstant());
    }
}
