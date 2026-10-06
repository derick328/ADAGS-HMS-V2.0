package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.ConsultationRecordStatus;
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
public class JdbcConsultationRecordRepository implements ConsultationRecordRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, author_id, status, history_of_presenting_illness, past_medical_history,
                   past_surgical_history, comorbidities, family_social_history, physical_examination, clinical_notes,
                   treatment_plan, retrospective_completion, retrospective_note, completed_at, abandoned_reason,
                   abandoned_at, abandoned_by, amends_id, amendment_reason, created_at, updated_at
            FROM consultation_record
            """;

    // Duplicated from JdbcEncounterRepository's own terminal-state list
    // rather than computed via EncounterState.isTerminal(): clinical cannot
    // import encounters.domain (backend/CLAUDE.md "Module boundary rules"
    // #2), and a literal list is what lets this query use an index instead
    // of a sequential scan, same reasoning as the encounters-module copy.
    // AWAITING_RESULTS is added on top of the terminal set — it is NOT
    // terminal, but item 5's CRITICAL rule excludes it anyway: a doctor
    // waiting on a five-day culture must not appear as incomplete.
    private static final String EXCLUDED_ENCOUNTER_STATES = """
            'LEFT_WITHOUT_BEING_SEEN', 'ABANDONED_UNPAID', 'CANCELLED_IN_ERROR',
            'REFERRED_OUT', 'DECEASED', 'CLOSED', 'DISCHARGED', 'AWAITING_RESULTS'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcConsultationRecordRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ConsultationRecord insert(ConsultationRecord record) {
        String sql = """
                INSERT INTO consultation_record (encounter_id, author_id, status, amends_id, amendment_reason)
                VALUES (:encounterId, :authorId, :status, :amendsId, :amendmentReason)
                RETURNING id, created_at, updated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("encounterId", record.encounterId())
                .addValue("authorId", record.authorId())
                .addValue("status", record.status().name())
                .addValue("amendsId", record.amendsId(), Types.OTHER)
                .addValue("amendmentReason", record.amendmentReason(), Types.VARCHAR);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ConsultationRecord(
                (UUID) rs.getObject("id"), record.encounterId(), record.authorId(), record.status(),
                null, null, null, null, null, null, null, null,
                false, null, null, null, null, null,
                record.amendsId(), record.amendmentReason(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
    }

    @Override
    public Optional<ConsultationRecord> findById(UUID id) {
        List<ConsultationRecord> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<ConsultationRecord> findOpenByEncounterAndAuthor(UUID encounterId, UUID authorId) {
        List<ConsultationRecord> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId AND author_id = :authorId AND status = 'OPEN'",
                new MapSqlParameterSource().addValue("encounterId", encounterId).addValue("authorId", authorId),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<ConsultationRecord> findByEncounterId(UUID encounterId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId ORDER BY created_at DESC",
                new MapSqlParameterSource("encounterId", encounterId),
                this::mapRow);
    }

    @Override
    public void autosave(UUID id, String historyOfPresentingIllness, String pastMedicalHistory, String pastSurgicalHistory,
                          String comorbidities, String familySocialHistory, String physicalExamination, String clinicalNotes,
                          String treatmentPlan) {
        jdbcTemplate.update("""
                        UPDATE consultation_record SET
                            history_of_presenting_illness = :historyOfPresentingIllness,
                            past_medical_history = :pastMedicalHistory,
                            past_surgical_history = :pastSurgicalHistory,
                            comorbidities = :comorbidities,
                            family_social_history = :familySocialHistory,
                            physical_examination = :physicalExamination,
                            clinical_notes = :clinicalNotes,
                            treatment_plan = :treatmentPlan,
                            updated_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("historyOfPresentingIllness", historyOfPresentingIllness, Types.VARCHAR)
                        .addValue("pastMedicalHistory", pastMedicalHistory, Types.VARCHAR)
                        .addValue("pastSurgicalHistory", pastSurgicalHistory, Types.VARCHAR)
                        .addValue("comorbidities", comorbidities, Types.VARCHAR)
                        .addValue("familySocialHistory", familySocialHistory, Types.VARCHAR)
                        .addValue("physicalExamination", physicalExamination, Types.VARCHAR)
                        .addValue("clinicalNotes", clinicalNotes, Types.VARCHAR)
                        .addValue("treatmentPlan", treatmentPlan, Types.VARCHAR)
                        .addValue("id", id));
    }

    @Override
    public void finalize(UUID id, Instant completedAt, boolean retrospectiveCompletion, String retrospectiveNote) {
        jdbcTemplate.update("""
                        UPDATE consultation_record
                        SET status = 'FINALIZED', completed_at = :completedAt,
                            retrospective_completion = :retrospectiveCompletion, retrospective_note = :retrospectiveNote,
                            updated_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("completedAt", Timestamp.from(completedAt))
                        .addValue("retrospectiveCompletion", retrospectiveCompletion)
                        .addValue("retrospectiveNote", retrospectiveNote, Types.VARCHAR)
                        .addValue("id", id));
    }

    @Override
    public List<ConsultationRecord> findIncomplete(UUID authorId, Instant threshold) {
        String sql = """
                SELECT cr.id, cr.encounter_id, cr.author_id, cr.status, cr.history_of_presenting_illness,
                       cr.past_medical_history, cr.past_surgical_history, cr.comorbidities, cr.family_social_history,
                       cr.physical_examination, cr.clinical_notes, cr.treatment_plan, cr.retrospective_completion,
                       cr.retrospective_note, cr.completed_at, cr.abandoned_reason, cr.abandoned_at,
                       cr.abandoned_by, cr.amends_id, cr.amendment_reason, cr.created_at, cr.updated_at
                FROM consultation_record cr
                JOIN encounter e ON e.id = cr.encounter_id
                WHERE cr.status = 'OPEN'
                  AND cr.author_id = :authorId
                  AND cr.created_at < :threshold
                  AND e.state NOT IN (""" + EXCLUDED_ENCOUNTER_STATES + ")";
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("authorId", authorId)
                        .addValue("threshold", Timestamp.from(threshold)),
                this::mapRow);
    }

    private ConsultationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp completedAt = rs.getTimestamp("completed_at");
        Timestamp abandonedAt = rs.getTimestamp("abandoned_at");
        return new ConsultationRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("author_id"),
                ConsultationRecordStatus.valueOf(rs.getString("status")),
                rs.getString("history_of_presenting_illness"),
                rs.getString("past_medical_history"),
                rs.getString("past_surgical_history"),
                rs.getString("comorbidities"),
                rs.getString("family_social_history"),
                rs.getString("physical_examination"),
                rs.getString("clinical_notes"),
                rs.getString("treatment_plan"),
                rs.getBoolean("retrospective_completion"),
                rs.getString("retrospective_note"),
                completedAt == null ? null : completedAt.toInstant(),
                rs.getString("abandoned_reason"),
                abandonedAt == null ? null : abandonedAt.toInstant(),
                (UUID) rs.getObject("abandoned_by"),
                (UUID) rs.getObject("amends_id"),
                rs.getString("amendment_reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
