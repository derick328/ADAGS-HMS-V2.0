package com.adags.modules.pharmacy.infrastructure;

import com.adags.modules.pharmacy.domain.Dispense;
import com.adags.modules.pharmacy.domain.DispenseRepository;
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
public class JdbcDispenseRepository implements DispenseRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, prescription_line_id, dispensed_by, dispensed_at, idempotency_key, quantity_dispensed,
                   counselling_notes, dispensing_notes
            FROM dispense
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDispenseRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Dispense insert(Dispense dispense) {
        String sql = """
                INSERT INTO dispense (id, prescription_line_id, dispensed_by, idempotency_key, quantity_dispensed,
                                       counselling_notes, dispensing_notes)
                VALUES (COALESCE(:id, gen_random_uuid()), :prescriptionLineId, :dispensedBy, :idempotencyKey,
                        :quantityDispensed, :counsellingNotes, :dispensingNotes)
                RETURNING id, dispensed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", dispense.id(), Types.OTHER)
                .addValue("prescriptionLineId", dispense.prescriptionLineId())
                .addValue("dispensedBy", dispense.dispensedBy())
                .addValue("idempotencyKey", dispense.idempotencyKey())
                .addValue("quantityDispensed", dispense.quantityDispensed())
                .addValue("counsellingNotes", dispense.counsellingNotes(), Types.VARCHAR)
                .addValue("dispensingNotes", dispense.dispensingNotes(), Types.VARCHAR);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Dispense(
                (UUID) rs.getObject("id"), dispense.prescriptionLineId(), dispense.dispensedBy(),
                rs.getTimestamp("dispensed_at").toInstant(), dispense.idempotencyKey(), dispense.quantityDispensed(),
                dispense.counsellingNotes(), dispense.dispensingNotes()));
    }

    @Override
    public Optional<Dispense> findById(UUID id) {
        List<Dispense> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Dispense> findByIdempotencyKey(String idempotencyKey) {
        List<Dispense> results = jdbcTemplate.query(SELECT_COLUMNS + " WHERE idempotency_key = :key",
                new MapSqlParameterSource("key", idempotencyKey), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Dispense> findByPrescriptionLineId(UUID prescriptionLineId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE prescription_line_id = :prescriptionLineId",
                new MapSqlParameterSource("prescriptionLineId", prescriptionLineId), this::mapRow);
    }

    private Dispense mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Dispense(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("prescription_line_id"),
                (UUID) rs.getObject("dispensed_by"),
                rs.getTimestamp("dispensed_at").toInstant(),
                rs.getString("idempotency_key"),
                rs.getInt("quantity_dispensed"),
                rs.getString("counselling_notes"),
                rs.getString("dispensing_notes"));
    }
}
