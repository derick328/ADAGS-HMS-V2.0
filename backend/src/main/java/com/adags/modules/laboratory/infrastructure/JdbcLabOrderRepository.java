package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.Urgency;
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
public class JdbcLabOrderRepository implements LabOrderRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, patient_id, facility_id, lab_test_id, ordered_by, ordered_at,
                   clinical_indication, urgency, invoice_line_id, created_at
            FROM lab_order
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabOrderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabOrder insert(LabOrder order) {
        String sql = """
                INSERT INTO lab_order (id, encounter_id, patient_id, facility_id, lab_test_id, ordered_by,
                                        clinical_indication, urgency, invoice_line_id)
                VALUES (COALESCE(:id, gen_random_uuid()), :encounterId, :patientId, :facilityId, :labTestId,
                        :orderedBy, :clinicalIndication, :urgency, :invoiceLineId)
                RETURNING id, ordered_at, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", order.id(), Types.OTHER)
                .addValue("encounterId", order.encounterId())
                .addValue("patientId", order.patientId())
                .addValue("facilityId", order.facilityId())
                .addValue("labTestId", order.labTestId())
                .addValue("orderedBy", order.orderedBy())
                .addValue("clinicalIndication", order.clinicalIndication())
                .addValue("urgency", order.urgency().name())
                .addValue("invoiceLineId", order.invoiceLineId());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabOrder(
                (UUID) rs.getObject("id"), order.encounterId(), order.patientId(), order.facilityId(),
                order.labTestId(), order.orderedBy(), rs.getTimestamp("ordered_at").toInstant(),
                order.clinicalIndication(), order.urgency(), order.invoiceLineId(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<LabOrder> findById(UUID id) {
        List<LabOrder> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    private LabOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LabOrder(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("facility_id"),
                (UUID) rs.getObject("lab_test_id"),
                (UUID) rs.getObject("ordered_by"),
                rs.getTimestamp("ordered_at").toInstant(),
                rs.getString("clinical_indication"),
                Urgency.valueOf(rs.getString("urgency")),
                (UUID) rs.getObject("invoice_line_id"),
                rs.getTimestamp("created_at").toInstant());
    }
}
