package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.CurrentStockRow;
import com.adags.modules.inventory.domain.ExpiringBatchRow;
import com.adags.modules.inventory.domain.LowStockRow;
import com.adags.modules.inventory.domain.StockValuationRow;
import com.adags.modules.inventory.domain.StockViewRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcStockViewRepository implements StockViewRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcStockViewRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CurrentStockRow> findCurrentStock() {
        return findCurrentStock(null);
    }

    @Override
    public List<CurrentStockRow> findCurrentStock(List<UUID> storeIds) {
        String sql = """
                SELECT i.id AS item_id, i.code AS item_code, i.name AS item_name,
                       i.strength, i.unit AS base_unit,
                       CASE WHEN b.expiry_date <= (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date
                            THEN 'EXPIRED' ELSE b.status END AS stock_status,
                       b.id AS batch_id, b.batch_number, b.expiry_date,
                       s.id AS store_id, s.name AS store_name, sb.quantity,
                       CASE WHEN b.status = 'ACTIVE' AND
                         b.expiry_date > (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date
                         THEN sb.quantity ELSE 0 END AS available_quantity
                FROM stock_balance sb
                JOIN item i ON i.id = sb.item_id
                JOIN stock_batch b ON b.id = sb.batch_id
                JOIN store s ON s.id = sb.store_id
                WHERE sb.quantity > 0
                ORDER BY i.name, b.expiry_date
                """;
        if (storeIds != null) sql = sql.replace("ORDER BY i.name", "AND sb.store_id IN (:storeIds) ORDER BY i.name");
        return jdbcTemplate.query(sql, new MapSqlParameterSource().addValue("storeIds", storeIds), (rs, rowNum) -> new CurrentStockRow(
                (UUID) rs.getObject("item_id"), rs.getString("item_code"), rs.getString("item_name"),
                (UUID) rs.getObject("batch_id"), rs.getString("batch_number"), rs.getDate("expiry_date").toLocalDate(),
                (UUID) rs.getObject("store_id"), rs.getString("store_name"), rs.getInt("quantity"),
                rs.getString("strength"), rs.getString("base_unit"), rs.getString("stock_status"),
                rs.getInt("available_quantity")));
    }

    @Override
    public List<LowStockRow> findLowStock() {
        return findLowStock(null);
    }

    @Override
    public List<LowStockRow> findLowStock(List<UUID> storeIds) {
        String sql = """
                SELECT i.id AS item_id, i.code AS item_code, i.name AS item_name, i.reorder_level,
                       COALESCE(SUM(sb.quantity), 0) AS total_quantity
                FROM item i
                LEFT JOIN stock_balance sb ON sb.item_id = i.id
                  AND EXISTS (SELECT 1 FROM stock_batch b WHERE b.id = sb.batch_id
                    AND b.status = 'ACTIVE'
                    AND b.expiry_date > (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date)
                GROUP BY i.id, i.code, i.name, i.reorder_level
                HAVING COALESCE(SUM(sb.quantity), 0) < i.reorder_level
                ORDER BY i.name
                """;
        if (storeIds != null) sql = sql.replace("LEFT JOIN stock_balance sb ON sb.item_id = i.id",
                "LEFT JOIN stock_balance sb ON sb.item_id = i.id AND sb.store_id IN (:storeIds)");
        return jdbcTemplate.query(sql, new MapSqlParameterSource().addValue("storeIds", storeIds), (rs, rowNum) -> new LowStockRow(
                (UUID) rs.getObject("item_id"), rs.getString("item_code"), rs.getString("item_name"),
                rs.getInt("total_quantity"), rs.getInt("reorder_level")));
    }

    @Override
    public List<ExpiringBatchRow> findExpiringWithin(int windowDays) {
        return findExpiringWithin(windowDays, null);
    }

    @Override
    public List<ExpiringBatchRow> findExpiringWithin(int windowDays, List<UUID> storeIds) {
        String sql = """
                SELECT b.id AS batch_id, i.id AS item_id, i.code AS item_code, i.name AS item_name,
                       b.batch_number, b.expiry_date, COALESCE(SUM(sb.quantity), 0) AS quantity
                FROM stock_batch b
                JOIN item i ON i.id = b.item_id
                LEFT JOIN stock_balance sb ON sb.batch_id = b.id
                WHERE b.status = 'ACTIVE' AND b.expiry_date BETWEEN
                    (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date
                    AND (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::date + :windowDays
                GROUP BY b.id, i.id, i.code, i.name, b.batch_number, b.expiry_date
                HAVING COALESCE(SUM(sb.quantity), 0) > 0
                ORDER BY b.expiry_date
                """;
        if (storeIds != null) sql = sql.replace("LEFT JOIN stock_balance sb ON sb.batch_id = b.id",
                "LEFT JOIN stock_balance sb ON sb.batch_id = b.id AND sb.store_id IN (:storeIds)");
        return jdbcTemplate.query(sql, new MapSqlParameterSource("windowDays", windowDays)
                .addValue("storeIds", storeIds), (rs, rowNum) -> new ExpiringBatchRow(
                (UUID) rs.getObject("batch_id"), (UUID) rs.getObject("item_id"), rs.getString("item_code"),
                rs.getString("item_name"), rs.getString("batch_number"), rs.getDate("expiry_date").toLocalDate(),
                rs.getInt("quantity")));
    }

    @Override
    public List<StockValuationRow> findStockValuation() {
        return findStockValuation(null);
    }

    @Override
    public List<StockValuationRow> findStockValuation(List<UUID> storeIds) {
        String sql = """
                SELECT s.id AS store_id, s.name AS store_name,
                       SUM(sb.quantity * b.unit_cost_amount_minor) AS total_value_minor,
                       MIN(b.unit_cost_currency_code) AS currency_code
                FROM stock_balance sb
                JOIN stock_batch b ON b.id = sb.batch_id
                JOIN store s ON s.id = sb.store_id
                GROUP BY s.id, s.name
                ORDER BY s.name
                """;
        if (storeIds != null) sql = sql.replace("GROUP BY s.id", "WHERE sb.store_id IN (:storeIds) GROUP BY s.id");
        return jdbcTemplate.query(sql, new MapSqlParameterSource().addValue("storeIds", storeIds), (rs, rowNum) -> new StockValuationRow(
                (UUID) rs.getObject("store_id"), rs.getString("store_name"),
                new Money(rs.getLong("total_value_minor"), rs.getString("currency_code"))));
    }
}
