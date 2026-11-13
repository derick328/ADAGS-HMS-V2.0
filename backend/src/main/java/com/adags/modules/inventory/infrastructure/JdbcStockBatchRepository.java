package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.RestockLedgerEntry;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockBatchStatus;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
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
    public PackagingRule packagingRule(UUID batchId) {
        return jdbcTemplate.queryForObject("""
                SELECT receipt_line_id, dispense_multiple_base FROM stock_batch WHERE id = :id
                """, new MapSqlParameterSource("id", batchId),
                (rs, row) -> new PackagingRule((UUID) rs.getObject("receipt_line_id"),
                        rs.getInt("dispense_multiple_base")));
    }

    @Override
    public List<StockBatch> findActiveByItemFefo(UUID itemId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE item_id = :itemId AND status = 'ACTIVE' "
                        + "AND expiry_date > (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date "
                        + "ORDER BY expiry_date ASC",
                new MapSqlParameterSource("itemId", itemId),
                this::mapRow);
    }

    @Override
    public List<StockBatch> findActiveByItemFefo(UUID itemId, UUID facilityId) {
        return jdbcTemplate.query("""
                SELECT b.id, b.item_id, b.store_id, b.batch_number, b.expiry_date,
                       b.unit_cost_amount_minor, b.unit_cost_currency_code, b.supplier_id,
                       b.delivery_note_ref, b.status, b.received_by, b.received_at, b.notes
                FROM stock_batch b JOIN store s ON s.id = b.store_id
                WHERE b.item_id = :itemId AND s.facility_id = :facilityId
                  AND b.status = 'ACTIVE'
                  AND b.expiry_date > (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date
                ORDER BY b.expiry_date ASC, b.received_at ASC
                """, new MapSqlParameterSource().addValue("itemId", itemId)
                .addValue("facilityId", facilityId), this::mapRow);
    }

    @Override
    public List<RestockLedgerEntry> findRestocksBetween(Instant from, Instant to) {
        return findRestocksBetween(from, to, null);
    }

    @Override
    public List<RestockLedgerEntry> findRestocksBetween(Instant from, Instant to, List<UUID> storeIds) {
        String sql = """
                SELECT sb.id           AS batch_id,
                       sb.item_id      AS item_id,
                       i.name          AS item_name,
                       sb.batch_number AS batch_number,
                       COALESCE(mv.qty_delta, 0) AS received_qty,
                       CASE WHEN sb.cost_known THEN sb.unit_cost_amount_minor END AS unit_cost_amount_minor,
                       sb.unit_cost_currency_code AS currency_code,
                       sb.supplier_id  AS supplier_id,
                       sup.name        AS supplier_name,
                       sb.delivery_note_ref AS delivery_note_ref,
                       sb.received_at  AS received_at,
                       sb.received_by  AS received_by_id,
                       u.username      AS received_by_name
                  FROM stock_batch sb
                  JOIN item i ON i.id = sb.item_id
                  JOIN supplier sup ON sup.id = sb.supplier_id
                  LEFT JOIN app_user u ON u.id = sb.received_by
                  LEFT JOIN LATERAL (
                      SELECT qty_delta FROM stock_movement
                       WHERE batch_id = sb.id AND movement_type = 'RECEIPT'
                       ORDER BY occurred_at ASC LIMIT 1
                  ) mv ON TRUE
                 WHERE sb.received_at >= :fromTs
                   AND sb.received_at <  :toTs
                 ORDER BY sb.received_at DESC
                """;
        if (storeIds != null) sql = sql.replace("ORDER BY sb.received_at DESC",
                "AND sb.store_id IN (:storeIds) ORDER BY sb.received_at DESC");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTs", Timestamp.from(from))
                .addValue("toTs", Timestamp.from(to)).addValue("storeIds", storeIds);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Long unitCost = rs.getObject("unit_cost_amount_minor", Long.class);
            int receivedQty = rs.getInt("received_qty");
            String currency = rs.getString("currency_code");
            return new RestockLedgerEntry(
                    (UUID) rs.getObject("batch_id"),
                    (UUID) rs.getObject("item_id"),
                    rs.getString("item_name"),
                    rs.getString("batch_number"),
                    receivedQty,
                    unitCost,
                    unitCost == null ? null : Math.multiplyExact(unitCost, receivedQty),
                    currency,
                    (UUID) rs.getObject("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getString("delivery_note_ref"),
                    rs.getTimestamp("received_at").toInstant(),
                    (UUID) rs.getObject("received_by_id"),
                    rs.getString("received_by_name"));
        });
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
