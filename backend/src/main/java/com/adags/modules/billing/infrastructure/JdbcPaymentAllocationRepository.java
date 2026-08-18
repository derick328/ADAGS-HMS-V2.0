package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.PaymentAllocation;
import com.adags.modules.billing.domain.PaymentAllocationRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcPaymentAllocationRepository implements PaymentAllocationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentAllocationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PaymentAllocation insert(PaymentAllocation allocation) {
        String sql = """
                INSERT INTO payment_allocation (payment_id, invoice_line_id, amount_minor, currency_code)
                VALUES (:paymentId, :invoiceLineId, :amountMinor, :currencyCode)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("paymentId", allocation.paymentId())
                .addValue("invoiceLineId", allocation.invoiceLineId())
                .addValue("amountMinor", allocation.amount().amountMinor())
                .addValue("currencyCode", allocation.amount().currencyCode());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new PaymentAllocation(
                (UUID) rs.getObject("id"), allocation.paymentId(), allocation.invoiceLineId(), allocation.amount(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<PaymentAllocation> findSettledByInvoiceLineId(UUID invoiceLineId) {
        String sql = """
                SELECT pa.id, pa.payment_id, pa.invoice_line_id, pa.amount_minor, pa.currency_code, pa.created_at
                FROM payment_allocation pa
                JOIN payment p ON p.id = pa.payment_id
                WHERE pa.invoice_line_id = :invoiceLineId AND p.status = 'SETTLED'
                ORDER BY pa.created_at
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("invoiceLineId", invoiceLineId), this::mapRow);
    }

    private PaymentAllocation mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentAllocation(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("payment_id"),
                (UUID) rs.getObject("invoice_line_id"),
                new Money(rs.getLong("amount_minor"), rs.getString("currency_code")),
                rs.getTimestamp("created_at").toInstant());
    }
}
