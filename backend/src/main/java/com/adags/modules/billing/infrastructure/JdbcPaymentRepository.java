package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.CashierShiftNotOpenException;
import com.adags.modules.billing.domain.Payment;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.billing.domain.PaymentRepository;
import com.adags.modules.billing.domain.PaymentStatus;
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
public class JdbcPaymentRepository implements PaymentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, patient_id, cashier_shift_id, amount_minor, currency_code, method, reference, status,
                   idempotency_key, received_by, received_at
            FROM payment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Payment insert(Payment payment) {
        // WHERE EXISTS is the atomic guard against a shift that closed
        // between the caller's read and this write (root CLAUDE.md
        // "Concurrency": a real constraint in the statement, not a
        // read-then-write application check).
        String sql = """
                INSERT INTO payment (patient_id, cashier_shift_id, amount_minor, currency_code, method, reference,
                                      status, idempotency_key, received_by)
                SELECT :patientId, :cashierShiftId, :amountMinor, :currencyCode, :method, :reference, :status,
                       :idempotencyKey, :receivedBy
                WHERE EXISTS (SELECT 1 FROM cashier_shift WHERE id = :cashierShiftId AND status = 'OPEN')
                RETURNING id, received_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("patientId", payment.patientId())
                .addValue("cashierShiftId", payment.cashierShiftId())
                .addValue("amountMinor", payment.amount().amountMinor())
                .addValue("currencyCode", payment.amount().currencyCode())
                .addValue("method", payment.method().name())
                .addValue("reference", payment.reference(), Types.VARCHAR)
                .addValue("status", payment.status().name())
                .addValue("idempotencyKey", payment.idempotencyKey())
                .addValue("receivedBy", payment.receivedBy());

        List<Payment> inserted = jdbcTemplate.query(sql, params, (rs, rowNum) -> new Payment(
                (UUID) rs.getObject("id"), payment.patientId(), payment.cashierShiftId(), payment.amount(),
                payment.method(), payment.reference(), payment.status(), payment.idempotencyKey(),
                payment.receivedBy(), rs.getTimestamp("received_at").toInstant()));

        if (inserted.isEmpty()) {
            throw new CashierShiftNotOpenException(
                    "Cashier shift " + payment.cashierShiftId() + " is not open — it closed while this payment was being recorded.");
        }
        return inserted.get(0);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        List<Payment> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE idempotency_key = :idempotencyKey",
                new MapSqlParameterSource("idempotencyKey", idempotencyKey), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        List<Payment> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Payment> findByCashierShiftId(UUID cashierShiftId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE cashier_shift_id = :cashierShiftId ORDER BY received_at",
                new MapSqlParameterSource("cashierShiftId", cashierShiftId), this::mapRow);
    }

    private Payment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Payment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("cashier_shift_id"),
                new Money(rs.getLong("amount_minor"), rs.getString("currency_code")),
                PaymentMethod.valueOf(rs.getString("method")),
                rs.getString("reference"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("idempotency_key"),
                (UUID) rs.getObject("received_by"),
                rs.getTimestamp("received_at").toInstant());
    }
}
