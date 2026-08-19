package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.MedicationAdministration;
import com.adags.modules.inpatient.domain.MedicationAdministrationRepository;
import com.adags.modules.inpatient.domain.MedicationAdministrationStatus;
import com.adags.modules.inpatient.domain.OmissionReason;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMedicationAdministrationRepository implements MedicationAdministrationRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, admission_id, prescription_line_id, scheduled_at, status, administered_at, administered_by,
                   dose_given, batch_id, witness_user_id, omission_reason, omission_notes, remarks, created_at
            FROM medication_administration
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMedicationAdministrationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertSchedule(List<MedicationAdministration> slots) {
        if (slots.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO medication_administration (admission_id, prescription_line_id, scheduled_at, status)
                VALUES (:admissionId, :prescriptionLineId, :scheduledAt, 'DUE')
                ON CONFLICT (prescription_line_id, scheduled_at) DO NOTHING
                """;
        MapSqlParameterSource[] batchParams = slots.stream()
                .map(slot -> new MapSqlParameterSource()
                        .addValue("admissionId", slot.admissionId())
                        .addValue("prescriptionLineId", slot.prescriptionLineId())
                        .addValue("scheduledAt", Timestamp.from(slot.scheduledAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(sql, batchParams);
    }

    @Override
    public Optional<MedicationAdministration> findById(UUID id) {
        List<MedicationAdministration> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<MedicationAdministration> findByAdmissionId(UUID admissionId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE admission_id = :admissionId ORDER BY scheduled_at",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    @Override
    public List<MedicationAdministration> findOverdueByAdmissionId(UUID admissionId, Instant now) {
        String sql = SELECT_COLUMNS + " WHERE admission_id = :admissionId AND status = 'DUE' "
                + "AND scheduled_at < :now ORDER BY scheduled_at";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("admissionId", admissionId)
                .addValue("now", Timestamp.from(now));
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    @Override
    public Optional<MedicationAdministration> mark(MedicationAdministration marked) {
        String sql = """
                UPDATE medication_administration
                SET status = :status, administered_at = :administeredAt, administered_by = :administeredBy,
                    dose_given = :doseGiven, batch_id = :batchId, witness_user_id = :witnessUserId,
                    omission_reason = :omissionReason, omission_notes = :omissionNotes, remarks = :remarks
                WHERE id = :id AND status = 'DUE'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", marked.status().name())
                .addValue("administeredAt", Timestamp.from(marked.administeredAt()))
                .addValue("administeredBy", marked.administeredBy())
                .addValue("doseGiven", marked.doseGiven(), Types.VARCHAR)
                .addValue("batchId", marked.batchId(), Types.OTHER)
                .addValue("witnessUserId", marked.witnessUserId(), Types.OTHER)
                .addValue("omissionReason", marked.omissionReason() == null ? null : marked.omissionReason().name(), Types.VARCHAR)
                .addValue("omissionNotes", marked.omissionNotes(), Types.VARCHAR)
                .addValue("remarks", marked.remarks(), Types.VARCHAR)
                .addValue("id", marked.id());
        int updated = jdbcTemplate.update(sql, params);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(marked.id());
    }

    private MedicationAdministration mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp administeredAt = rs.getTimestamp("administered_at");
        String omissionReason = rs.getString("omission_reason");
        return new MedicationAdministration(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("admission_id"),
                (UUID) rs.getObject("prescription_line_id"),
                rs.getTimestamp("scheduled_at").toInstant(),
                MedicationAdministrationStatus.valueOf(rs.getString("status")),
                administeredAt == null ? null : administeredAt.toInstant(),
                (UUID) rs.getObject("administered_by"),
                rs.getString("dose_given"),
                (UUID) rs.getObject("batch_id"),
                (UUID) rs.getObject("witness_user_id"),
                omissionReason == null ? null : OmissionReason.valueOf(omissionReason),
                rs.getString("omission_notes"),
                rs.getString("remarks"),
                rs.getTimestamp("created_at").toInstant());
    }
}
