package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.InsufficientStockException;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcStockBalanceRepository implements StockBalanceRepository {

    // A non-negative delta (receipt) upserts: first receipt of a batch
    // creates the row, later ones accumulate onto it. quantity >= 0 is the
    // real guard against two concurrent callers oversubscribing a batch —
    // same row-locking mechanism JdbcGaplessNumberAllocator uses.
    private static final String INCREMENT_SQL = """
            INSERT INTO stock_balance (item_id, batch_id, store_id, quantity)
            VALUES (:itemId, :batchId, :storeId, :delta)
            ON CONFLICT (item_id, batch_id, store_id)
            DO UPDATE SET quantity = stock_balance.quantity + :delta
            RETURNING quantity
            """;

    // A negative delta (a dispense) is a plain UPDATE, never an upsert —
    // deliberately not the same INSERT ... ON CONFLICT statement as
    // INCREMENT_SQL. Postgres validates a CHECK constraint against the
    // INSERT's VALUES-clause candidate row even when ON CONFLICT is about
    // to redirect to DO UPDATE, so a negative delta in that VALUES clause
    // would fail the quantity >= 0 check immediately — before the conflict
    // is even evaluated — regardless of how much the existing row actually
    // holds. A row-less UPDATE also correctly signals "nothing to
    // decrement here" via zero rows affected, rather than an upsert
    // silently fabricating a balance row for a batch that was never
    // received into this store.
    private static final String DECREMENT_SQL = """
            UPDATE stock_balance
            SET quantity = quantity + :delta
            WHERE item_id = :itemId AND batch_id = :batchId AND store_id = :storeId
            RETURNING quantity
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStockBalanceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int applyDelta(UUID itemId, UUID batchId, UUID storeId, int delta) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("itemId", itemId)
                .addValue("batchId", batchId)
                .addValue("storeId", storeId)
                .addValue("delta", delta);
        try {
            List<Integer> result = jdbcTemplate.query(
                    delta >= 0 ? INCREMENT_SQL : DECREMENT_SQL, params, (rs, rowNum) -> rs.getInt("quantity"));
            return result.stream().findFirst().orElseThrow(() -> new InsufficientStockException(batchId));
        } catch (DataIntegrityViolationException ex) {
            throw new InsufficientStockException(batchId);
        }
    }

    @Override
    public int quantityOnHand(UUID batchId) {
        // Lab transfers can create another balance row for the same batch.
        // Dispensing availability is the batch's source pharmacy store only.
        List<Integer> result = jdbcTemplate.query(
                """
                SELECT sb.quantity FROM stock_balance sb JOIN stock_batch b ON b.id = sb.batch_id
                WHERE sb.batch_id = :batchId AND sb.store_id = b.store_id
                """,
                new MapSqlParameterSource("batchId", batchId), (rs, rowNum) -> rs.getInt("quantity"));
        return result.stream().findFirst().orElse(0);
    }
}
