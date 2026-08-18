package com.adags.modules.pharmacy.infrastructure;

import com.adags.modules.pharmacy.domain.SubstitutionProposal;
import com.adags.modules.pharmacy.domain.SubstitutionProposalRepository;
import com.adags.modules.pharmacy.domain.SubstitutionStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSubstitutionProposalRepository implements SubstitutionProposalRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, prescription_line_id, proposed_item_id, reason, proposed_by, proposed_at, status,
                   reviewed_by, reviewed_at, review_note, replacement_line_id
            FROM substitution_proposal
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSubstitutionProposalRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SubstitutionProposal insert(SubstitutionProposal proposal) {
        String sql = """
                INSERT INTO substitution_proposal (id, prescription_line_id, proposed_item_id, reason, proposed_by)
                VALUES (COALESCE(:id, gen_random_uuid()), :lineId, :proposedItemId, :reason, :proposedBy)
                RETURNING id, proposed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", proposal.id(), Types.OTHER)
                .addValue("lineId", proposal.prescriptionLineId())
                .addValue("proposedItemId", proposal.proposedItemId())
                .addValue("reason", proposal.reason())
                .addValue("proposedBy", proposal.proposedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SubstitutionProposal(
                (UUID) rs.getObject("id"), proposal.prescriptionLineId(), proposal.proposedItemId(), proposal.reason(),
                proposal.proposedBy(), rs.getTimestamp("proposed_at").toInstant(), SubstitutionStatus.PROPOSED,
                null, null, null, null));
    }

    @Override
    public Optional<SubstitutionProposal> findById(UUID id) {
        List<SubstitutionProposal> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public SubstitutionProposal update(SubstitutionProposal proposal) {
        String sql = """
                UPDATE substitution_proposal
                SET status = :status, reviewed_by = :reviewedBy, reviewed_at = :reviewedAt,
                    review_note = :reviewNote, replacement_line_id = :replacementLineId
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", proposal.id())
                .addValue("status", proposal.status().name())
                .addValue("reviewedBy", proposal.reviewedBy(), Types.OTHER)
                .addValue("reviewedAt", proposal.reviewedAt() == null ? null : Timestamp.from(proposal.reviewedAt()))
                .addValue("reviewNote", proposal.reviewNote(), Types.VARCHAR)
                .addValue("replacementLineId", proposal.replacementLineId(), Types.OTHER));
        return proposal;
    }

    private SubstitutionProposal mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        return new SubstitutionProposal(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("prescription_line_id"),
                (UUID) rs.getObject("proposed_item_id"),
                rs.getString("reason"),
                (UUID) rs.getObject("proposed_by"),
                rs.getTimestamp("proposed_at").toInstant(),
                SubstitutionStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("reviewed_by"),
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getString("review_note"),
                (UUID) rs.getObject("replacement_line_id"));
    }
}
