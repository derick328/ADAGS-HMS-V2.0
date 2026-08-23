package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.PharmacyItemProposalRepository;
import com.adags.modules.inventory.domain.ProposalStatus;
import com.adags.modules.inventory.domain.ProposalType;
import com.adags.shared.kernel.Money;
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
public class JdbcPharmacyItemProposalRepository implements PharmacyItemProposalRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, proposal_type, item_id, proposed_name, proposed_classification, proposed_form, proposed_unit,
                   proposed_price_minor, proposed_price_currency, current_price_minor, justification, proposed_by,
                   proposed_at, status, reviewed_by, reviewed_at, review_note, created_item_id
            FROM pharmacy_item_proposal
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPharmacyItemProposalRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PharmacyItemProposal insert(PharmacyItemProposal proposal) {
        String sql = """
                INSERT INTO pharmacy_item_proposal (id, proposal_type, item_id, proposed_name, proposed_classification,
                                                     proposed_form, proposed_unit, proposed_price_minor,
                                                     proposed_price_currency, current_price_minor, justification, proposed_by)
                VALUES (COALESCE(:id, gen_random_uuid()), :proposalType, :itemId, :proposedName, :proposedClassification,
                        :proposedForm, :proposedUnit, :proposedPriceMinor, :proposedPriceCurrency, :currentPriceMinor,
                        :justification, :proposedBy)
                RETURNING id, proposed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", proposal.id(), Types.OTHER)
                .addValue("proposalType", proposal.proposalType().name())
                .addValue("itemId", proposal.itemId(), Types.OTHER)
                .addValue("proposedName", proposal.proposedName(), Types.VARCHAR)
                .addValue("proposedClassification", proposal.proposedClassification(), Types.VARCHAR)
                .addValue("proposedForm", proposal.proposedForm(), Types.VARCHAR)
                .addValue("proposedUnit", proposal.proposedUnit(), Types.VARCHAR)
                .addValue("proposedPriceMinor", proposal.proposedPrice().amountMinor())
                .addValue("proposedPriceCurrency", proposal.proposedPrice().currencyCode())
                .addValue("currentPriceMinor", proposal.currentPrice() == null ? null : proposal.currentPrice().amountMinor(), Types.BIGINT)
                .addValue("justification", proposal.justification(), Types.VARCHAR)
                .addValue("proposedBy", proposal.proposedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new PharmacyItemProposal(
                (UUID) rs.getObject("id"), proposal.proposalType(), proposal.itemId(), proposal.proposedName(),
                proposal.proposedClassification(), proposal.proposedForm(), proposal.proposedUnit(),
                proposal.proposedPrice(), proposal.currentPrice(), proposal.justification(), proposal.proposedBy(),
                rs.getTimestamp("proposed_at").toInstant(), ProposalStatus.PROPOSED, null, null, null, null));
    }

    @Override
    public Optional<PharmacyItemProposal> findById(UUID id) {
        List<PharmacyItemProposal> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<PharmacyItemProposal> findPending() {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = 'PROPOSED' ORDER BY proposed_at",
                this::mapRow);
    }

    @Override
    public PharmacyItemProposal update(PharmacyItemProposal proposal) {
        String sql = """
                UPDATE pharmacy_item_proposal
                SET status = :status, reviewed_by = :reviewedBy, reviewed_at = :reviewedAt,
                    review_note = :reviewNote, created_item_id = :createdItemId
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", proposal.id())
                .addValue("status", proposal.status().name())
                .addValue("reviewedBy", proposal.reviewedBy(), Types.OTHER)
                .addValue("reviewedAt", proposal.reviewedAt() == null ? null : Timestamp.from(proposal.reviewedAt()))
                .addValue("reviewNote", proposal.reviewNote(), Types.VARCHAR)
                .addValue("createdItemId", proposal.createdItemId(), Types.OTHER));
        return proposal;
    }

    private PharmacyItemProposal mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        Long currentPriceMinor = (Long) rs.getObject("current_price_minor");
        return new PharmacyItemProposal(
                (UUID) rs.getObject("id"),
                ProposalType.valueOf(rs.getString("proposal_type")),
                (UUID) rs.getObject("item_id"),
                rs.getString("proposed_name"),
                rs.getString("proposed_classification"),
                rs.getString("proposed_form"),
                rs.getString("proposed_unit"),
                new Money(rs.getLong("proposed_price_minor"), rs.getString("proposed_price_currency")),
                currentPriceMinor == null ? null : new Money(currentPriceMinor, rs.getString("proposed_price_currency")),
                rs.getString("justification"),
                (UUID) rs.getObject("proposed_by"),
                rs.getTimestamp("proposed_at").toInstant(),
                ProposalStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("reviewed_by"),
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getString("review_note"),
                (UUID) rs.getObject("created_item_id"));
    }
}
