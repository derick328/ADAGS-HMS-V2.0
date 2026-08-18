package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftNotClosedException;
import com.adags.modules.billing.domain.CashierShiftNotOpenException;
import com.adags.modules.billing.domain.CashierShiftRepository;
import com.adags.modules.billing.domain.CashierShiftStatus;
import com.adags.shared.kernel.Money;
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
public class JdbcCashierShiftRepository implements CashierShiftRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, facility_id, opened_by, opened_at, status, closed_by, closed_at, counted_cash_minor,
                   expected_cash_minor, variance_minor, reconciled_by, reconciled_at, currency_code
            FROM cashier_shift
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCashierShiftRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CashierShift open(CashierShift shift) {
        String sql = """
                INSERT INTO cashier_shift (facility_id, opened_by)
                VALUES (:facilityId, :openedBy)
                RETURNING id
                """;
        UUID id = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("facilityId", shift.facilityId())
                .addValue("openedBy", shift.openedBy()), UUID.class);
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<CashierShift> findById(UUID id) {
        List<CashierShift> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<CashierShift> findOpenByOpenedBy(UUID openedBy) {
        List<CashierShift> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE opened_by = :openedBy AND status = 'OPEN'",
                new MapSqlParameterSource("openedBy", openedBy), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public CashierShift close(UUID shiftId, UUID closedBy, Money countedCash) {
        // expected is derived fresh from payment rows at the moment of
        // close, not accumulated — same "trust the ledger for truth"
        // reasoning as invoice totals.
        String sql = """
                UPDATE cashier_shift
                SET status = 'CLOSED',
                    closed_by = :closedBy,
                    closed_at = now(),
                    counted_cash_minor = :countedCashMinor,
                    expected_cash_minor = sub.expected,
                    variance_minor = :countedCashMinor - sub.expected
                FROM (
                    SELECT COALESCE(SUM(amount_minor), 0) AS expected
                    FROM payment
                    WHERE cashier_shift_id = :shiftId AND method = 'CASH' AND status = 'SETTLED'
                ) sub
                WHERE cashier_shift.id = :shiftId AND status = 'OPEN'
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("shiftId", shiftId)
                .addValue("closedBy", closedBy)
                .addValue("countedCashMinor", countedCash.amountMinor()));
        if (updated == 0) {
            throw new CashierShiftNotOpenException(
                    "Cashier shift " + shiftId + " is not open — it may already be closed.");
        }
        return findById(shiftId).orElseThrow();
    }

    @Override
    public CashierShift reconcile(UUID shiftId, UUID reconciledBy) {
        String sql = """
                UPDATE cashier_shift
                SET status = 'RECONCILED', reconciled_by = :reconciledBy, reconciled_at = now()
                WHERE id = :shiftId AND status = 'CLOSED'
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("shiftId", shiftId)
                .addValue("reconciledBy", reconciledBy));
        if (updated == 0) {
            throw new CashierShiftNotClosedException(
                    "Cashier shift " + shiftId + " is not CLOSED — it must be closed by the cashier before it can be reconciled.");
        }
        return findById(shiftId).orElseThrow();
    }

    private CashierShift mapRow(ResultSet rs, int rowNum) throws SQLException {
        String currencyCode = rs.getString("currency_code");
        return new CashierShift(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("facility_id"),
                (UUID) rs.getObject("opened_by"),
                rs.getTimestamp("opened_at").toInstant(),
                CashierShiftStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("closed_by"),
                toInstant(rs.getTimestamp("closed_at")),
                toMoney(rs, "counted_cash_minor", currencyCode),
                toMoney(rs, "expected_cash_minor", currencyCode),
                toMoney(rs, "variance_minor", currencyCode),
                (UUID) rs.getObject("reconciled_by"),
                toInstant(rs.getTimestamp("reconciled_at")));
    }

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Money toMoney(ResultSet rs, String column, String currencyCode) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new Money(value, currencyCode);
    }
}
