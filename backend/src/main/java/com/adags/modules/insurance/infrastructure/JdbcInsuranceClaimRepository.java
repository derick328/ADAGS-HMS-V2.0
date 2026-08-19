package com.adags.modules.insurance.infrastructure;

import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.InsuranceClaim;
import com.adags.modules.insurance.domain.InsuranceClaimRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcInsuranceClaimRepository implements InsuranceClaimRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, invoice_id, scheme_id, claim_no, state, submitted_at, approved_minor, rejected_reason,
                   batch_id, created_at
            FROM insurance_claim
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInsuranceClaimRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InsuranceClaim insertDraft(InsuranceClaim claim) {
        String sql = """
                INSERT INTO insurance_claim (invoice_id, scheme_id)
                VALUES (:invoiceId, :schemeId)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoiceId", claim.invoiceId())
                .addValue("schemeId", claim.schemeId());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new InsuranceClaim(
                (UUID) rs.getObject("id"), claim.invoiceId(), claim.schemeId(), null, ClaimState.DRAFT, null, null,
                null, null, rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<InsuranceClaim> findById(UUID id) {
        List<InsuranceClaim> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    private InsuranceClaim mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp submittedAt = rs.getTimestamp("submitted_at");
        Object approvedMinor = rs.getObject("approved_minor");
        Object batchId = rs.getObject("batch_id");
        return new InsuranceClaim(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("invoice_id"),
                (UUID) rs.getObject("scheme_id"),
                rs.getString("claim_no"),
                ClaimState.valueOf(rs.getString("state")),
                submittedAt == null ? null : submittedAt.toInstant(),
                approvedMinor == null ? null : new Money(((Number) approvedMinor).longValue(), "TZS"),
                rs.getString("rejected_reason"),
                batchId == null ? null : (UUID) batchId,
                rs.getTimestamp("created_at").toInstant());
    }
}
