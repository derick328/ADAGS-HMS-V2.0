package com.adags.modules.pharmacy.infrastructure;

import com.adags.modules.pharmacy.domain.DispenseLine;
import com.adags.modules.pharmacy.domain.DispenseLineRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcDispenseLineRepository implements DispenseLineRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, dispense_id, item_id, batch_id, quantity, unit_cost_amount_minor, unit_cost_currency_code,
                   fefo_override_reason, created_at, requested_quantity, requested_unit, conversion_factor, cost_known
            FROM dispense_line
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDispenseLineRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DispenseLine insert(DispenseLine line) {
        String sql = """
                INSERT INTO dispense_line (id, dispense_id, item_id, batch_id, quantity, unit_cost_amount_minor,
                                            unit_cost_currency_code, fefo_override_reason,
                                            requested_quantity, requested_unit, conversion_factor, cost_known)
                VALUES (COALESCE(:id, gen_random_uuid()), :dispenseId, :itemId, :batchId, :quantity,
                        :unitCostAmountMinor, :unitCostCurrencyCode, :fefoOverrideReason,
                        :requestedQuantity, :requestedUnit, :conversionFactor,
                        (SELECT cost_known FROM stock_batch WHERE id = :batchId))
                RETURNING id, created_at, cost_known
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", line.id(), Types.OTHER)
                .addValue("dispenseId", line.dispenseId())
                .addValue("itemId", line.itemId())
                .addValue("batchId", line.batchId())
                .addValue("quantity", line.quantity())
                .addValue("unitCostAmountMinor", line.unitCost().amountMinor())
                .addValue("unitCostCurrencyCode", line.unitCost().currencyCode())
                .addValue("fefoOverrideReason", line.fefoOverrideReason(), Types.VARCHAR)
                .addValue("requestedQuantity", line.requestedQuantity())
                .addValue("requestedUnit", line.requestedUnit(), Types.VARCHAR)
                .addValue("conversionFactor", line.conversionFactor());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new DispenseLine(
                (UUID) rs.getObject("id"), line.dispenseId(), line.itemId(), line.batchId(), line.quantity(),
                line.unitCost(), line.fefoOverrideReason(), rs.getTimestamp("created_at").toInstant(),
                line.requestedQuantity(), line.requestedUnit(), line.conversionFactor(), rs.getBoolean("cost_known")));
    }

    @Override
    public List<DispenseLine> findByDispenseId(UUID dispenseId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE dispense_id = :dispenseId",
                new MapSqlParameterSource("dispenseId", dispenseId), this::mapRow);
    }

    private DispenseLine mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DispenseLine(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("dispense_id"),
                (UUID) rs.getObject("item_id"),
                (UUID) rs.getObject("batch_id"),
                rs.getInt("quantity"),
                new Money(rs.getLong("unit_cost_amount_minor"), rs.getString("unit_cost_currency_code")),
                rs.getString("fefo_override_reason"),
                rs.getTimestamp("created_at").toInstant(), rs.getInt("requested_quantity"),
                rs.getString("requested_unit"), rs.getInt("conversion_factor"), rs.getBoolean("cost_known"));
    }
}
