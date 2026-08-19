package com.adags.modules.insurance.infrastructure;

import com.adags.modules.insurance.domain.InsuranceScheme;
import com.adags.modules.insurance.domain.InsuranceSchemeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInsuranceSchemeRepository implements InsuranceSchemeRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, code, name, active, claim_submission_method, contact, payment_terms_days, created_at
            FROM insurance_scheme
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInsuranceSchemeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InsuranceScheme insert(InsuranceScheme scheme) {
        String sql = """
                INSERT INTO insurance_scheme (code, name, active, claim_submission_method, contact, payment_terms_days)
                VALUES (:code, :name, :active, :claimSubmissionMethod, :contact, :paymentTermsDays)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", scheme.code())
                .addValue("name", scheme.name())
                .addValue("active", scheme.active())
                .addValue("claimSubmissionMethod", scheme.claimSubmissionMethod())
                .addValue("contact", scheme.contact())
                .addValue("paymentTermsDays", scheme.paymentTermsDays());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new InsuranceScheme(
                (UUID) rs.getObject("id"), scheme.code(), scheme.name(), scheme.active(),
                scheme.claimSubmissionMethod(), scheme.contact(), scheme.paymentTermsDays(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<InsuranceScheme> findById(UUID id) {
        List<InsuranceScheme> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<InsuranceScheme> findByCode(String code) {
        List<InsuranceScheme> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE code = :code",
                new MapSqlParameterSource("code", code), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<InsuranceScheme> findAllActive() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE active = TRUE ORDER BY name",
                new MapSqlParameterSource(), this::mapRow);
    }

    private InsuranceScheme mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object paymentTermsDays = rs.getObject("payment_terms_days");
        return new InsuranceScheme(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBoolean("active"),
                rs.getString("claim_submission_method"),
                rs.getString("contact"),
                paymentTermsDays == null ? null : ((Number) paymentTermsDays).intValue(),
                rs.getTimestamp("created_at").toInstant());
    }
}
