package com.adags.modules.encounters.infrastructure;

import com.adags.modules.encounters.domain.ImmediateReason;
import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.PregnancyStatus;
import com.adags.modules.encounters.domain.TriageAssessment;
import com.adags.modules.encounters.domain.TriageAssessmentRepository;
import com.adags.modules.encounters.domain.TriagePriority;
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
public class JdbcTriageAssessmentRepository implements TriageAssessmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, chief_complaint, current_symptoms, allergies, comorbidities,
                   mode_of_ambulation, has_pain, pain_score, pain_location, fall_risk, fall_score,
                   infection_risk, pregnancy_status, gestational_weeks, priority, immediate_reason,
                   immediate_reason_other_text, notes, supersedes_id, assessed_by, assessed_at
            FROM triage_assessment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTriageAssessmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TriageAssessment insert(TriageAssessment triageAssessment) {
        // id defaults to gen_random_uuid() (matching the column's own
        // DEFAULT) unless the caller pre-generated one — the submission
        // transaction does this so VitalSigns.context_ref_id can point at
        // this row's id before this insert has even run (see
        // TriageAssessment.withId).
        String sql = """
                INSERT INTO triage_assessment (
                    id, encounter_id, chief_complaint, current_symptoms, allergies, comorbidities,
                    mode_of_ambulation, has_pain, pain_score, pain_location, fall_risk, fall_score,
                    infection_risk, pregnancy_status, gestational_weeks, priority, immediate_reason,
                    immediate_reason_other_text, notes, supersedes_id, assessed_by)
                VALUES (
                    COALESCE(:id, gen_random_uuid()), :encounterId, :chiefComplaint, :currentSymptoms, :allergies,
                    :comorbidities, :modeOfAmbulation, :hasPain, :painScore, :painLocation, :fallRisk, :fallScore,
                    :infectionRisk, :pregnancyStatus, :gestationalWeeks, :priority, :immediateReason,
                    :immediateReasonOtherText, :notes, :supersedesId, :assessedBy)
                RETURNING id, assessed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", triageAssessment.id(), Types.OTHER)
                .addValue("encounterId", triageAssessment.encounterId())
                .addValue("chiefComplaint", triageAssessment.chiefComplaint())
                .addValue("currentSymptoms", triageAssessment.currentSymptoms())
                .addValue("allergies", triageAssessment.allergies())
                .addValue("comorbidities", triageAssessment.comorbidities())
                .addValue("modeOfAmbulation", triageAssessment.modeOfAmbulation().name())
                .addValue("hasPain", triageAssessment.hasPain())
                .addValue("painScore", triageAssessment.painScore(), Types.SMALLINT)
                .addValue("painLocation", triageAssessment.painLocation(), Types.VARCHAR)
                .addValue("fallRisk", triageAssessment.fallRisk())
                .addValue("fallScore", triageAssessment.fallScore(), Types.SMALLINT)
                .addValue("infectionRisk", triageAssessment.infectionRisk())
                .addValue("pregnancyStatus",
                        triageAssessment.pregnancyStatus() == null ? null : triageAssessment.pregnancyStatus().name(),
                        Types.VARCHAR)
                .addValue("gestationalWeeks", triageAssessment.gestationalWeeks(), Types.SMALLINT)
                .addValue("priority", triageAssessment.priority().name())
                .addValue("immediateReason",
                        triageAssessment.immediateReason() == null ? null : triageAssessment.immediateReason().name(),
                        Types.VARCHAR)
                .addValue("immediateReasonOtherText", triageAssessment.immediateReasonOtherText(), Types.VARCHAR)
                .addValue("notes", triageAssessment.notes(), Types.VARCHAR)
                .addValue("supersedesId", triageAssessment.supersedesId(), Types.OTHER)
                .addValue("assessedBy", triageAssessment.assessedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> withGeneratedFields(
                triageAssessment, (UUID) rs.getObject("id"), rs.getTimestamp("assessed_at").toInstant()));
    }

    private TriageAssessment withGeneratedFields(TriageAssessment source, UUID id, java.time.Instant assessedAt) {
        return new TriageAssessment(
                id, source.encounterId(), source.chiefComplaint(), source.currentSymptoms(), source.allergies(),
                source.comorbidities(), source.modeOfAmbulation(), source.hasPain(), source.painScore(),
                source.painLocation(), source.fallRisk(), source.fallScore(), source.infectionRisk(),
                source.pregnancyStatus(), source.gestationalWeeks(), source.priority(), source.immediateReason(),
                source.immediateReasonOtherText(), source.notes(), source.supersedesId(), source.assessedBy(),
                assessedAt);
    }

    @Override
    public Optional<TriageAssessment> findById(UUID id) {
        List<TriageAssessment> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<TriageAssessment> findByEncounterId(UUID encounterId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId ORDER BY assessed_at DESC",
                new MapSqlParameterSource("encounterId", encounterId),
                this::mapRow);
    }

    @Override
    public List<TriageAssessment> search(UUID assessedBy, Instant assessedFrom, Instant assessedTo) {
        String sql = SELECT_COLUMNS + """
                 WHERE assessed_at >= :assessedFrom AND assessed_at < :assessedTo
                   AND (CAST(:assessedBy AS UUID) IS NULL OR assessed_by = CAST(:assessedBy AS UUID))
                   AND id NOT IN (SELECT supersedes_id FROM triage_assessment WHERE supersedes_id IS NOT NULL)
                 ORDER BY assessed_at DESC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("assessedFrom", Timestamp.from(assessedFrom))
                .addValue("assessedTo", Timestamp.from(assessedTo))
                .addValue("assessedBy", assessedBy, Types.OTHER),
                this::mapRow);
    }

    private TriageAssessment mapRow(ResultSet rs, int rowNum) throws SQLException {
        String pregnancyStatus = rs.getString("pregnancy_status");
        String immediateReason = rs.getString("immediate_reason");
        return new TriageAssessment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                rs.getString("chief_complaint"),
                rs.getString("current_symptoms"),
                rs.getString("allergies"),
                rs.getString("comorbidities"),
                ModeOfAmbulation.valueOf(rs.getString("mode_of_ambulation")),
                rs.getBoolean("has_pain"),
                getNullableInt(rs, "pain_score"),
                rs.getString("pain_location"),
                rs.getBoolean("fall_risk"),
                getNullableInt(rs, "fall_score"),
                rs.getBoolean("infection_risk"),
                pregnancyStatus == null ? null : PregnancyStatus.valueOf(pregnancyStatus),
                getNullableInt(rs, "gestational_weeks"),
                TriagePriority.valueOf(rs.getString("priority")),
                immediateReason == null ? null : ImmediateReason.valueOf(immediateReason),
                rs.getString("immediate_reason_other_text"),
                rs.getString("notes"),
                (UUID) rs.getObject("supersedes_id"),
                (UUID) rs.getObject("assessed_by"),
                rs.getTimestamp("assessed_at").toInstant());
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
