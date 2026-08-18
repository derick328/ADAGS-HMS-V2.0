package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockBatchStatus;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcStockBatchRepository implements StockBatchRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, item_id, store_id, batch_number, expiry_date, unit_cost_amount_minor,
                   unit_cost_currency_code, supplier_id, delivery_note_ref, status, received_by,
                   received_at, notes
            FROM stock_batch
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStockBatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StockBatch insert(StockBatch batch) {
        String sql = """
                INSERT INTO stock_batch (id, item_id, store_id, batch_number, expiry_date, unit_cost_amount_minor,
                                          unit_cost_currency_code, supplier_id, delivery_note_ref, status,
                                          received_by, notes)
                VALUES (COALESCE(:id, gen_random_uuid()), :itemId, :storeId, :batchNumber, :expiryDate,
                        :unitCostAmountMinor, :unitCostCurrencyCode, :supplierId, :deliveryNoteRef, :status,
                        :receivedBy, :notes)
                RETURNING id, received_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", batch.id(), Types.OTHER)
                .addValue("itemId", batch.itemId())
                .addValue("storeId", batch.storeId())
                .addValue("batchNumber", batch.batchNumber())
                .addValue("expiryDate", batch.expiryDate())
                .addValue("unitCostAmountMinor", batch.unitCost().amountMinor())
                .addValue("unitCostCurrencyCode", batch.unitCost().currencyCode())
                .addValue("supplierId", batch.supplierId())
                .addValue("deliveryNoteRef", batch.deliveryNoteRef())
                .addValue("status", batch.status().name())
                .addValue("receivedBy", batch.receivedBy())
                .addValue("notes", batch.notes(), Types.VARCHAR);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new StockBatch(
                (UUID) rs.getObject("id"), batch.itemId(), batch.storeId(), batch.batchNumber(), batch.expiryDate(),
                batch.unitCost(), batch.supplierId(), batch.deliveryNoteRef(), batch.status(), batch.receivedBy(),
                rs.getTimestamp("received_at").toInstant(), batch.notes()));
    }

    @Override
    public Optional<StockBatch> findById(UUID id) {
        List<StockBatch> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<StockBatch> findActiveByItemFefo(UUID itemId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE item_id = :itemId AND status = 'ACTIVE' AND expiry_date > CURRENT_DATE "
                        + "ORDER BY expiry_date ASC",
                new MapSqlParameterSource("itemId", itemId),
                this::mapRow);
    }

    private StockBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StockBatch(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("item_id"),
                (UUID) rs.getObject("store_id"),
                rs.getString("batch_number"),
                rs.getDate("expiry_date").toLocalDate(),
                new Money(rs.getLong("unit_cost_amount_minor"), rs.getString("unit_cost_currency_code")),
                (UUID) rs.getObject("supplier_id"),
                rs.getString("delivery_note_ref"),
                StockBatchStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("received_by"),
                rs.getTimestamp("received_at").toInstant(),
                rs.getString("notes"));
    }
}
