package com.adags.modules.pharmacy.infrastructure;

import com.adags.modules.pharmacy.domain.PrescriptionFrequency;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionLineStatus;
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
public class JdbcPrescriptionLineRepository implements PrescriptionLineRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, prescription_order_id, item_id, quantity_prescribed, quantity_dispensed,
                   dosage_instructions, invoice_line_id, status, substitution_allowed, status_reason,
                   route, dose_amount, dose_unit, frequency, duration_days, created_at
            FROM prescription_line
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPrescriptionLineRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PrescriptionLine insert(PrescriptionLine line) {
        String sql = """
                INSERT INTO prescription_line (id, prescription_order_id, item_id, quantity_prescribed,
                                                dosage_instructions, invoice_line_id, substitution_allowed,
                                                route, dose_amount, dose_unit, frequency, duration_days)
                VALUES (COALESCE(:id, gen_random_uuid()), :orderId, :itemId, :quantityPrescribed,
                        :dosageInstructions, :invoiceLineId, :substitutionAllowed,
                        :route, :doseAmount, :doseUnit, :frequency, :durationDays)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", line.id(), Types.OTHER)
                .addValue("orderId", line.prescriptionOrderId())
                .addValue("itemId", line.itemId())
                .addValue("quantityPrescribed", line.quantityPrescribed())
                .addValue("dosageInstructions", line.dosageInstructions())
                .addValue("invoiceLineId", line.invoiceLineId())
                .addValue("substitutionAllowed", line.substitutionAllowed())
                .addValue("route", line.route(), Types.VARCHAR)
                .addValue("doseAmount", line.doseAmount(), Types.NUMERIC)
                .addValue("doseUnit", line.doseUnit(), Types.VARCHAR)
                .addValue("frequency", line.frequency() == null ? null : line.frequency().name(), Types.VARCHAR)
                .addValue("durationDays", line.durationDays(), Types.INTEGER);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new PrescriptionLine(
                (UUID) rs.getObject("id"), line.prescriptionOrderId(), line.itemId(), line.quantityPrescribed(),
                0, line.dosageInstructions(), line.invoiceLineId(), PrescriptionLineStatus.AWAITING_DISPENSE,
                line.substitutionAllowed(), null, line.route(), line.doseAmount(), line.doseUnit(),
                line.frequency(), line.durationDays(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<PrescriptionLine> findById(UUID id) {
        List<PrescriptionLine> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<PrescriptionLine> findByPrescriptionOrderId(UUID prescriptionOrderId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE prescription_order_id = :prescriptionOrderId ORDER BY created_at",
                new MapSqlParameterSource("prescriptionOrderId", prescriptionOrderId),
                this::mapRow);
    }

    @Override
    public PrescriptionLine update(PrescriptionLine line) {
        String sql = """
                UPDATE prescription_line
                SET quantity_dispensed = :quantityDispensed, status = :status, status_reason = :statusReason
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", line.id())
                .addValue("quantityDispensed", line.quantityDispensed())
                .addValue("status", line.status().name())
                .addValue("statusReason", line.statusReason(), Types.VARCHAR));
        return line;
    }

    private PrescriptionLine mapRow(ResultSet rs, int rowNum) throws SQLException {
        String frequency = rs.getString("frequency");
        Double doseAmount = (Double) rs.getObject("dose_amount");
        Integer durationDays = (Integer) rs.getObject("duration_days");
        return new PrescriptionLine(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("prescription_order_id"),
                (UUID) rs.getObject("item_id"),
                rs.getInt("quantity_prescribed"),
                rs.getInt("quantity_dispensed"),
                rs.getString("dosage_instructions"),
                (UUID) rs.getObject("invoice_line_id"),
                PrescriptionLineStatus.valueOf(rs.getString("status")),
                rs.getBoolean("substitution_allowed"),
                rs.getString("status_reason"),
                rs.getString("route"),
                doseAmount,
                rs.getString("dose_unit"),
                frequency == null ? null : PrescriptionFrequency.valueOf(frequency),
                durationDays,
                rs.getTimestamp("created_at").toInstant());
    }
}
