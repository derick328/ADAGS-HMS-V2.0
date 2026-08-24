package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabItemProposal;
import com.adags.modules.laboratory.domain.LabItemProposalParameter;
import com.adags.modules.laboratory.domain.LabItemProposalRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcLabItemProposalRepository implements LabItemProposalRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabItemProposalRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public LabItemProposal insert(LabItemProposal proposal) {
        String sql = """
                INSERT INTO lab_item_proposal (proposed_name, proposed_classification, proposed_price_minor,
                                                proposed_price_currency, notes, specimen_type, container_type,
                                                expected_tat_hours, justification, proposed_by, status)
                VALUES (:proposedName, :proposedClassification, :proposedPriceMinor, :proposedPriceCurrency,
                        :notes, :specimenType, :containerType, :expectedTatHours, :justification, :proposedBy,
                        :status)
                RETURNING id, proposed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("proposedName", proposal.proposedName())
                .addValue("proposedClassification", proposal.proposedClassification())
                .addValue("proposedPriceMinor", proposal.proposedPrice() == null ? null : proposal.proposedPrice().amountMinor())
                .addValue("proposedPriceCurrency", proposal.proposedPrice() == null ? "TZS" : proposal.proposedPrice().currencyCode())
                .addValue("notes", proposal.notes())
                .addValue("specimenType", proposal.specimenType())
                .addValue("containerType", proposal.containerType())
                .addValue("expectedTatHours", proposal.expectedTatHours())
                .addValue("justification", proposal.justification())
                .addValue("proposedBy", proposal.proposedBy())
                .addValue("status", proposal.status().name());

        UUID id = jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> (UUID) rs.getObject("id"));

        List<LabItemProposalParameter> savedParameters = new ArrayList<>();
        int sortOrder = 0;
        for (LabItemProposalParameter parameter : proposal.parameters()) {
            savedParameters.add(insertParameter(id, parameter, sortOrder++));
        }

        return new LabItemProposal(id, proposal.proposedName(), proposal.proposedClassification(),
                proposal.proposedPrice(), proposal.notes(), proposal.specimenType(), proposal.containerType(),
                proposal.expectedTatHours(), proposal.justification(), proposal.proposedBy(), null,
                proposal.status(), null, null, null, null, savedParameters);
    }

    private LabItemProposalParameter insertParameter(UUID proposalId, LabItemProposalParameter parameter, int sortOrder) {
        String sql = """
                INSERT INTO lab_item_proposal_parameter (proposal_id, name, unit, method, sort_order)
                VALUES (:proposalId, :name, :unit, :method, :sortOrder)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("proposalId", proposalId)
                .addValue("name", parameter.name())
                .addValue("unit", parameter.unit())
                .addValue("method", parameter.method())
                .addValue("sortOrder", sortOrder);
        UUID id = jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> (UUID) rs.getObject("id"));
        return new LabItemProposalParameter(id, proposalId, parameter.name(), parameter.unit(), parameter.method(), sortOrder);
    }
}
