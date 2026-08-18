package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.domain.AllergyConfirmationStatus;
import com.adags.modules.clinical.domain.AllergyRecord;
import com.adags.modules.clinical.domain.AllergyRecordRepository;
import com.adags.modules.clinical.domain.AllergySource;
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
public class JdbcAllergyRecordRepository implements AllergyRecordRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, patient_id, encounter_id, description, reaction, severity, source, recorded_by, recorded_at,
                   confirmation_status, confirmed_by, confirmed_at, confirmation_note, supersedes_id
            FROM allergy_record
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAllergyRecordRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AllergyRecord insert(AllergyRecord allergyRecord) {
        String sql = """
                INSERT INTO allergy_record (patient_id, encounter_id, description, reaction, severity, source,
                                             recorded_by, supersedes_id)
                VALUES (:patientId, :encounterId, :description, :reaction, :severity, :source,
                        :recordedBy, :supersedesId)
                RETURNING id, recorded_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("patientId", allergyRecord.patientId())
                .addValue("encounterId", allergyRecord.encounterId(), Types.OTHER)
                .addValue("description", allergyRecord.description())
                .addValue("reaction", allergyRecord.reaction(), Types.VARCHAR)
                .addValue("severity", allergyRecord.severity(), Types.VARCHAR)
                .addValue("source", allergyRecord.source().name())
                .addValue("recordedBy", allergyRecord.recordedBy())
                .addValue("supersedesId", allergyRecord.supersedesId(), Types.OTHER);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new AllergyRecord(
                (UUID) rs.getObject("id"),
                allergyRecord.patientId(),
                allergyRecord.encounterId(),
                allergyRecord.description(),
                allergyRecord.reaction(),
                allergyRecord.severity(),
                allergyRecord.source(),
                allergyRecord.recordedBy(),
                rs.getTimestamp("recorded_at").toInstant(),
                AllergyConfirmationStatus.UNCONFIRMED,
                null, null, null,
                allergyRecord.supersedesId()));
    }

    @Override
    public Optional<AllergyRecord> findById(UUID id) {
        List<AllergyRecord> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<AllergyRecord> findByPatientId(UUID patientId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE patient_id = :patientId ORDER BY recorded_at DESC",
                new MapSqlParameterSource("patientId", patientId),
                this::mapRow);
    }

    @Override
    public void reconcile(UUID id, AllergyConfirmationStatus decision, UUID confirmedBy, Instant confirmedAt, String confirmationNote) {
        jdbcTemplate.update("""
                        UPDATE allergy_record
                        SET confirmation_status = :status, confirmed_by = :confirmedBy,
                            confirmed_at = :confirmedAt, confirmation_note = :confirmationNote
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("status", decision.name())
                        .addValue("confirmedBy", confirmedBy)
                        .addValue("confirmedAt", Timestamp.from(confirmedAt))
                        .addValue("confirmationNote", confirmationNote, Types.VARCHAR)
                        .addValue("id", id));
    }

    private AllergyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp confirmedAt = rs.getTimestamp("confirmed_at");
        return new AllergyRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("encounter_id"),
                rs.getString("description"),
                rs.getString("reaction"),
                rs.getString("severity"),
                AllergySource.valueOf(rs.getString("source")),
                (UUID) rs.getObject("recorded_by"),
                rs.getTimestamp("recorded_at").toInstant(),
                AllergyConfirmationStatus.valueOf(rs.getString("confirmation_status")),
                (UUID) rs.getObject("confirmed_by"),
                confirmedAt == null ? null : confirmedAt.toInstant(),
                rs.getString("confirmation_note"),
                (UUID) rs.getObject("supersedes_id"));
    }
}
