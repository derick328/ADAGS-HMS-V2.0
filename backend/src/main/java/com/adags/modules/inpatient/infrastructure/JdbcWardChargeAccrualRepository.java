package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.DuplicateAccrualException;
import com.adags.modules.inpatient.domain.WardChargeAccrual;
import com.adags.modules.inpatient.domain.WardChargeAccrualRepository;
import com.adags.shared.kernel.Money;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcWardChargeAccrualRepository implements WardChargeAccrualRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, admission_id, bed_assignment_id, ward_id, accrual_boundary_at,
                   daily_rate_minor, currency_code, invoice_line_id, created_at
            FROM ward_charge_accrual
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWardChargeAccrualRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<WardChargeAccrual> findByAdmissionId(UUID admissionId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE admission_id = :admissionId ORDER BY accrual_boundary_at",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    @Override
    public Optional<Instant> findLastPostedBoundary(UUID admissionId) {
        Timestamp max = jdbcTemplate.queryForObject(
                "SELECT max(accrual_boundary_at) FROM ward_charge_accrual WHERE admission_id = :admissionId AND invoice_line_id IS NOT NULL",
                new MapSqlParameterSource("admissionId", admissionId), Timestamp.class);
        return Optional.ofNullable(max).map(Timestamp::toInstant);
    }

    @Override
    public WardChargeAccrual reserve(WardChargeAccrual accrual) {
        String sql = """
                INSERT INTO ward_charge_accrual (admission_id, bed_assignment_id, ward_id, accrual_boundary_at,
                                                  daily_rate_minor, currency_code)
                VALUES (:admissionId, :bedAssignmentId, :wardId, :accrualBoundaryAt, :dailyRateMinor, :currencyCode)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("admissionId", accrual.admissionId())
                .addValue("bedAssignmentId", accrual.bedAssignmentId())
                .addValue("wardId", accrual.wardId())
                .addValue("accrualBoundaryAt", Timestamp.from(accrual.accrualBoundaryAt()))
                .addValue("dailyRateMinor", accrual.dailyRate().amountMinor())
                .addValue("currencyCode", accrual.dailyRate().currencyCode());

        try {
            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new WardChargeAccrual(
                    (UUID) rs.getObject("id"), accrual.admissionId(), accrual.bedAssignmentId(), accrual.wardId(),
                    accrual.accrualBoundaryAt(), accrual.dailyRate(), null, rs.getTimestamp("created_at").toInstant()));
        } catch (DuplicateKeyException raceLost) {
            throw new DuplicateAccrualException(accrual.bedAssignmentId(), accrual.accrualBoundaryAt());
        }
    }

    @Override
    public void attachInvoiceLine(UUID accrualId, UUID invoiceLineId) {
        jdbcTemplate.update(
                "UPDATE ward_charge_accrual SET invoice_line_id = :invoiceLineId WHERE id = :id",
                new MapSqlParameterSource().addValue("invoiceLineId", invoiceLineId).addValue("id", accrualId));
    }

    private WardChargeAccrual mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WardChargeAccrual(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("admission_id"),
                (UUID) rs.getObject("bed_assignment_id"),
                (UUID) rs.getObject("ward_id"),
                rs.getTimestamp("accrual_boundary_at").toInstant(),
                new Money(rs.getLong("daily_rate_minor"), rs.getString("currency_code")),
                (UUID) rs.getObject("invoice_line_id"),
                rs.getTimestamp("created_at").toInstant());
    }
}
