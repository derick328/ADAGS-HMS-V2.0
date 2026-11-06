package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenQuality;
import com.adags.modules.laboratory.domain.SpecimenRepository;
import com.adags.modules.laboratory.domain.SpecimenStatus;
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
public class JdbcSpecimenRepository implements SpecimenRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, lab_order_id, accession_no, sample_type, container_type, collected_by, collected_at,
                   received_by, received_at, quality, quality_note, status, rejection_reason, created_at, sample_type_override_reason
            FROM specimen
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSpecimenRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Specimen insert(Specimen specimen) {
        String sql = """
                INSERT INTO specimen (lab_order_id, accession_no, sample_type, container_type, collected_by,
                                       collected_at, received_by, received_at, quality, quality_note, status,
                                       rejection_reason, sample_type_override_reason)
                VALUES (:labOrderId, :accessionNo, :sampleType, :containerType, :collectedBy,
                        :collectedAt, :receivedBy, :receivedAt, :quality, :qualityNote, :status,
                        :rejectionReason, :sampleTypeOverrideReason)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("labOrderId", specimen.labOrderId())
                .addValue("accessionNo", specimen.accessionNo())
                .addValue("sampleType", specimen.sampleType())
                .addValue("containerType", specimen.containerType())
                .addValue("collectedBy", specimen.collectedBy())
                .addValue("collectedAt", java.sql.Timestamp.from(specimen.collectedAt()))
                .addValue("receivedBy", specimen.receivedBy(), Types.OTHER)
                .addValue("receivedAt", specimen.receivedAt() == null ? null : java.sql.Timestamp.from(specimen.receivedAt()))
                .addValue("quality", specimen.quality() == null ? null : specimen.quality().name())
                .addValue("qualityNote", specimen.qualityNote())
                .addValue("status", specimen.status().name())
                .addValue("rejectionReason", specimen.rejectionReason())
                .addValue("sampleTypeOverrideReason", specimen.sampleTypeOverrideReason());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Specimen(
                (UUID) rs.getObject("id"), specimen.labOrderId(), specimen.accessionNo(), specimen.sampleType(),
                specimen.containerType(), specimen.collectedBy(), specimen.collectedAt(), specimen.receivedBy(),
                specimen.receivedAt(), specimen.quality(), specimen.qualityNote(), specimen.status(),
                specimen.rejectionReason(), rs.getTimestamp("created_at").toInstant(),
                specimen.sampleTypeOverrideReason()));
    }

    @Override
    public Optional<Specimen> findById(UUID id) {
        List<Specimen> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Specimen> findByLabOrderId(UUID labOrderId) {
        String sql = SELECT_COLUMNS + " WHERE lab_order_id = :labOrderId ORDER BY collected_at DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("labOrderId", labOrderId), this::mapRow);
    }

    private Specimen mapRow(ResultSet rs, int rowNum) throws SQLException {
        String quality = rs.getString("quality");
        java.sql.Timestamp receivedAt = rs.getTimestamp("received_at");
        return new Specimen(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("lab_order_id"),
                rs.getString("accession_no"),
                rs.getString("sample_type"),
                rs.getString("container_type"),
                (UUID) rs.getObject("collected_by"),
                rs.getTimestamp("collected_at").toInstant(),
                (UUID) rs.getObject("received_by"),
                receivedAt == null ? null : receivedAt.toInstant(),
                quality == null ? null : SpecimenQuality.valueOf(quality),
                rs.getString("quality_note"),
                SpecimenStatus.valueOf(rs.getString("status")),
                rs.getString("rejection_reason"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("sample_type_override_reason"));
    }
}
