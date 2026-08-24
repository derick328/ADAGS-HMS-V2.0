package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.VerificationLevel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLabTestRepository implements LabTestRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, code, name, specimen_type, container_type, expected_tat_hours, verification_level,
                   produces_machine_output, is_active, created_at
            FROM lab_test
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabTestRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabTest insert(LabTest test) {
        String sql = """
                INSERT INTO lab_test (code, name, specimen_type, container_type, expected_tat_hours,
                                       verification_level, produces_machine_output, is_active)
                VALUES (:code, :name, :specimenType, :containerType, :expectedTatHours,
                        :verificationLevel, :producesMachineOutput, :isActive)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", test.code())
                .addValue("name", test.name())
                .addValue("specimenType", test.specimenType())
                .addValue("containerType", test.containerType())
                .addValue("expectedTatHours", test.expectedTatHours())
                .addValue("verificationLevel", test.verificationLevel().name())
                .addValue("producesMachineOutput", test.producesMachineOutput())
                .addValue("isActive", test.isActive());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabTest(
                (UUID) rs.getObject("id"), test.code(), test.name(), test.specimenType(), test.containerType(),
                test.expectedTatHours(), test.verificationLevel(), test.producesMachineOutput(), test.isActive(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<LabTest> findById(UUID id) {
        List<LabTest> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<LabTest> findByCode(String code) {
        List<LabTest> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE code = :code", new MapSqlParameterSource("code", code), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<LabTest> findAllActive() {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE is_active = TRUE ORDER BY name", this::mapRow);
    }

    private LabTest mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LabTest(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("specimen_type"),
                rs.getString("container_type"),
                rs.getInt("expected_tat_hours"),
                VerificationLevel.valueOf(rs.getString("verification_level")),
                rs.getBoolean("produces_machine_output"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant());
    }
}
