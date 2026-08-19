package com.adags.modules.insurance.infrastructure;

import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.InsuranceClaim;
import com.adags.modules.insurance.domain.InsuranceClaimRepository;
import com.adags.modules.insurance.domain.InsuranceClaimStateConflictException;
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
    public InsuranceClaim insert(InsuranceClaim claim) {
        String sql = """
                INSERT INTO insurance_claim (invoice_id, scheme_id, claim_no)
                VALUES (:invoiceId, :schemeId, :claimNo)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoiceId", claim.invoiceId())
                .addValue("schemeId", claim.schemeId())
                .addValue("claimNo", claim.claimNo());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new InsuranceClaim(
                (UUID) rs.getObject("id"), claim.invoiceId(), claim.schemeId(), claim.claimNo(), ClaimState.DRAFT,
                null, null, null, null, rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<InsuranceClaim> findById(UUID id) {
        List<InsuranceClaim> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public InsuranceClaim transition(UUID claimId, ClaimState expectedCurrentState, ClaimState newState, String rejectedReason) {
        // submitted_at advances on either kind of submission event — the
        // first (SUBMITTED) or a resubmission (RESUBMITTED) — never on
        // APPROVED/REJECTED, which aren't submission events themselves.
        String sql = """
                UPDATE insurance_claim
                SET state = :newState,
                    submitted_at = CASE WHEN :newState IN ('SUBMITTED', 'RESUBMITTED') THEN now() ELSE submitted_at END,
                    rejected_reason = CASE WHEN :newState = 'REJECTED' THEN :rejectedReason ELSE rejected_reason END
                WHERE id = :id AND state = :expectedCurrentState
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", claimId)
                .addValue("expectedCurrentState", expectedCurrentState.name())
                .addValue("newState", newState.name())
                .addValue("rejectedReason", rejectedReason));
        if (updated == 0) {
            throw new InsuranceClaimStateConflictException(claimId, expectedCurrentState);
        }
        return findById(claimId).orElseThrow();
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
