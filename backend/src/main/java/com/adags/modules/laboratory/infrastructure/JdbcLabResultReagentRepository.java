package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabResultReagent;
import com.adags.modules.laboratory.domain.LabResultReagentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcLabResultReagentRepository implements LabResultReagentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabResultReagentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabResultReagent insert(LabResultReagent reagent) {
        String sql = """
                INSERT INTO lab_result_reagent (result_id, stock_batch_id, quantity_used, note)
                VALUES (:resultId, :stockBatchId, :quantityUsed, :note)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("resultId", reagent.resultId())
                .addValue("stockBatchId", reagent.stockBatchId(), Types.OTHER)
                .addValue("quantityUsed", reagent.quantityUsed())
                .addValue("note", reagent.note());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabResultReagent(
                (UUID) rs.getObject("id"), reagent.resultId(), reagent.stockBatchId(), reagent.quantityUsed(),
                reagent.note(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<LabResultReagent> findByResultId(UUID resultId) {
        String sql = """
                SELECT id, result_id, stock_batch_id, quantity_used, note, created_at
                FROM lab_result_reagent
                WHERE result_id = :resultId
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("resultId", resultId), this::mapRow);
    }

    private LabResultReagent mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object stockBatchId = rs.getObject("stock_batch_id");
        Object quantityUsed = rs.getObject("quantity_used");
        return new LabResultReagent(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("result_id"),
                stockBatchId == null ? null : (UUID) stockBatchId,
                quantityUsed == null ? null : ((Number) quantityUsed).doubleValue(),
                rs.getString("note"),
                rs.getTimestamp("created_at").toInstant());
    }
}
