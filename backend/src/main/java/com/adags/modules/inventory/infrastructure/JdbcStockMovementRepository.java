package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.StockMovement;
import com.adags.modules.inventory.domain.StockMovementRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.UUID;

@Repository
public class JdbcStockMovementRepository implements StockMovementRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStockMovementRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StockMovement insert(StockMovement movement) {
        String sql = """
                INSERT INTO stock_movement (id, item_id, batch_id, store_id, qty_delta, movement_type,
                                             reference_type, reference_id, actor_id)
                VALUES (COALESCE(:id, gen_random_uuid()), :itemId, :batchId, :storeId, :qtyDelta, :movementType,
                        :referenceType, :referenceId, :actorId)
                RETURNING id, occurred_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", movement.id(), Types.OTHER)
                .addValue("itemId", movement.itemId())
                .addValue("batchId", movement.batchId())
                .addValue("storeId", movement.storeId())
                .addValue("qtyDelta", movement.qtyDelta())
                .addValue("movementType", movement.movementType().name())
                .addValue("referenceType", movement.referenceType(), Types.VARCHAR)
                .addValue("referenceId", movement.referenceId(), Types.OTHER)
                .addValue("actorId", movement.actorId());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new StockMovement(
                (UUID) rs.getObject("id"), movement.itemId(), movement.batchId(), movement.storeId(),
                movement.qtyDelta(), movement.movementType(), movement.referenceType(), movement.referenceId(),
                movement.actorId(), rs.getTimestamp("occurred_at").toInstant()));
    }
}
