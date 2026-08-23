package com.adags.modules.surgery.infrastructure;

import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemRepository;
import com.adags.modules.surgery.domain.ProcedurePhase;
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
public class JdbcProcedureItemRepository implements ProcedureItemRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, surgery_order_id, item_id, phase, quantity_prepared, quantity_used, quantity_returned,
                   quantity_wasted, wastage_reason, batch_id, prepared_by, prepared_at, reconciled_by,
                   attested_by, reconciled_at
            FROM procedure_item
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProcedureItemRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProcedureItem insert(ProcedureItem procedureItem) {
        String sql = """
                INSERT INTO procedure_item (
                    id, surgery_order_id, item_id, phase, quantity_prepared, quantity_used, quantity_returned,
                    quantity_wasted, wastage_reason, batch_id, prepared_by)
                VALUES (
                    :id, :surgeryOrderId, :itemId, :phase, :quantityPrepared, :quantityUsed, :quantityReturned,
                    :quantityWasted, :wastageReason, :batchId, :preparedBy)
                RETURNING id, prepared_at
                """;
        UUID id = procedureItem.id() == null ? UUID.randomUUID() : procedureItem.id();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("surgeryOrderId", procedureItem.surgeryOrderId())
                .addValue("itemId", procedureItem.itemId())
                .addValue("phase", procedureItem.phase().name())
                .addValue("quantityPrepared", procedureItem.quantityPrepared())
                .addValue("quantityUsed", procedureItem.quantityUsed())
                .addValue("quantityReturned", procedureItem.quantityReturned())
                .addValue("quantityWasted", procedureItem.quantityWasted())
                .addValue("wastageReason", procedureItem.wastageReason())
                .addValue("batchId", procedureItem.batchId())
                .addValue("preparedBy", procedureItem.preparedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ProcedureItem(
                (UUID) rs.getObject("id"), procedureItem.surgeryOrderId(), procedureItem.itemId(),
                procedureItem.phase(), procedureItem.quantityPrepared(), procedureItem.quantityUsed(),
                procedureItem.quantityReturned(), procedureItem.quantityWasted(), procedureItem.wastageReason(),
                procedureItem.batchId(), procedureItem.preparedBy(), rs.getTimestamp("prepared_at").toInstant(),
                null, null, null));
    }

    @Override
    public Optional<ProcedureItem> findById(UUID id) {
        List<ProcedureItem> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<ProcedureItem> findBySurgeryOrderId(UUID surgeryOrderId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE surgery_order_id = :surgeryOrderId ORDER BY prepared_at",
                new MapSqlParameterSource("surgeryOrderId", surgeryOrderId), this::mapRow);
    }

    @Override
    public boolean reconcile(UUID procedureItemId, int quantityUsed, int quantityReturned, int quantityWasted,
                              String wastageReason, UUID reconciledBy) {
        int updated = jdbcTemplate.update("""
                        UPDATE procedure_item
                        SET quantity_used = :quantityUsed, quantity_returned = :quantityReturned,
                            quantity_wasted = :quantityWasted, wastage_reason = :wastageReason,
                            reconciled_by = :reconciledBy, reconciled_at = now()
                        WHERE id = :id AND reconciled_by IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("id", procedureItemId)
                        .addValue("quantityUsed", quantityUsed)
                        .addValue("quantityReturned", quantityReturned)
                        .addValue("quantityWasted", quantityWasted)
                        .addValue("wastageReason", wastageReason)
                        .addValue("reconciledBy", reconciledBy));
        return updated > 0;
    }

    @Override
    public boolean attest(UUID procedureItemId, UUID attestedBy) {
        int updated = jdbcTemplate.update("""
                        UPDATE procedure_item
                        SET attested_by = :attestedBy
                        WHERE id = :id AND reconciled_by IS NOT NULL AND attested_by IS NULL
                        """,
                new MapSqlParameterSource().addValue("id", procedureItemId).addValue("attestedBy", attestedBy));
        return updated > 0;
    }

    private ProcedureItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp reconciledAt = rs.getTimestamp("reconciled_at");
        return new ProcedureItem(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("surgery_order_id"),
                (UUID) rs.getObject("item_id"),
                ProcedurePhase.valueOf(rs.getString("phase")),
                rs.getInt("quantity_prepared"),
                rs.getInt("quantity_used"),
                rs.getInt("quantity_returned"),
                rs.getInt("quantity_wasted"),
                rs.getString("wastage_reason"),
                (UUID) rs.getObject("batch_id"),
                (UUID) rs.getObject("prepared_by"),
                rs.getTimestamp("prepared_at").toInstant(),
                (UUID) rs.getObject("reconciled_by"),
                (UUID) rs.getObject("attested_by"),
                reconciledAt == null ? null : reconciledAt.toInstant());
    }
}
