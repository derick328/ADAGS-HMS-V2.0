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
        String sql = """
                SELECT i.id AS item_id, i.code AS item_code, i.name AS item_name,
                       b.id AS batch_id, b.batch_number, b.expiry_date,
                       s.id AS store_id, s.name AS store_name, sb.quantity
                FROM stock_balance sb
                JOIN item i ON i.id = sb.item_id
                JOIN stock_batch b ON b.id = sb.batch_id
                JOIN store s ON s.id = sb.store_id
                WHERE sb.quantity > 0
                ORDER BY i.name, b.expiry_date
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new CurrentStockRow(
                (UUID) rs.getObject("item_id"), rs.getString("item_code"), rs.getString("item_name"),
                (UUID) rs.getObject("batch_id"), rs.getString("batch_number"), rs.getDate("expiry_date").toLocalDate(),
                (UUID) rs.getObject("store_id"), rs.getString("store_name"), rs.getInt("quantity")));
    }

    @Override
    public List<LowStockRow> findLowStock() {
        String sql = """
                SELECT i.id AS item_id, i.code AS item_code, i.name AS item_name, i.reorder_level,
                       COALESCE(SUM(sb.quantity), 0) AS total_quantity
                FROM item i
                LEFT JOIN stock_balance sb ON sb.item_id = i.id
                GROUP BY i.id, i.code, i.name, i.reorder_level
                HAVING COALESCE(SUM(sb.quantity), 0) < i.reorder_level
                ORDER BY i.name
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new LowStockRow(
                (UUID) rs.getObject("item_id"), rs.getString("item_code"), rs.getString("item_name"),
                rs.getInt("total_quantity"), rs.getInt("reorder_level")));
    }

    @Override
    public List<ExpiringBatchRow> findExpiringWithin(int windowDays) {
        String sql = """
                SELECT b.id AS batch_id, i.id AS item_id, i.code AS item_code, i.name AS item_name,
                       b.batch_number, b.expiry_date, COALESCE(SUM(sb.quantity), 0) AS quantity
                FROM stock_batch b
                JOIN item i ON i.id = b.item_id
                LEFT JOIN stock_balance sb ON sb.batch_id = b.id
                WHERE b.status = 'ACTIVE' AND b.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + (:windowDays || ' days')::interval
                GROUP BY b.id, i.id, i.code, i.name, b.batch_number, b.expiry_date
                ORDER BY b.expiry_date
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("windowDays", windowDays), (rs, rowNum) -> new ExpiringBatchRow(
                (UUID) rs.getObject("batch_id"), (UUID) rs.getObject("item_id"), rs.getString("item_code"),
                rs.getString("item_name"), rs.getString("batch_number"), rs.getDate("expiry_date").toLocalDate(),
                rs.getInt("quantity")));
    }

    @Override
    public List<StockValuationRow> findStockValuation() {
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
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new StockValuationRow(
                (UUID) rs.getObject("store_id"), rs.getString("store_name"),
                new Money(rs.getLong("total_value_minor"), rs.getString("currency_code"))));
    }
}
