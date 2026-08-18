package com.adags.modules.pharmacy.infrastructure;

import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
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
public class JdbcPrescriptionOrderRepository implements PrescriptionOrderRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, encounter_id, patient_id, facility_id, prescribed_by, prescribed_at, created_at FROM prescription_order";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPrescriptionOrderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PrescriptionOrder insert(PrescriptionOrder order) {
        String sql = """
                INSERT INTO prescription_order (id, encounter_id, patient_id, facility_id, prescribed_by)
                VALUES (COALESCE(:id, gen_random_uuid()), :encounterId, :patientId, :facilityId, :prescribedBy)
                RETURNING id, prescribed_at, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", order.id(), Types.OTHER)
                .addValue("encounterId", order.encounterId())
                .addValue("patientId", order.patientId())
                .addValue("facilityId", order.facilityId())
                .addValue("prescribedBy", order.prescribedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new PrescriptionOrder(
                (UUID) rs.getObject("id"), order.encounterId(), order.patientId(), order.facilityId(),
                order.prescribedBy(), rs.getTimestamp("prescribed_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<PrescriptionOrder> findById(UUID id) {
        List<PrescriptionOrder> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    private PrescriptionOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PrescriptionOrder(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("facility_id"),
                (UUID) rs.getObject("prescribed_by"),
                rs.getTimestamp("prescribed_at").toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
