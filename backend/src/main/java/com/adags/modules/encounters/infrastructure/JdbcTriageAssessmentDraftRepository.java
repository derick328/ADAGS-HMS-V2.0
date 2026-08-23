package com.adags.modules.encounters.infrastructure;

import com.adags.modules.encounters.domain.ModeOfAmbulation;
import com.adags.modules.encounters.domain.TriageAssessmentDraft;
import com.adags.modules.encounters.domain.TriageAssessmentDraftRepository;
import com.adags.modules.encounters.domain.TriagePriority;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTriageAssessmentDraftRepository implements TriageAssessmentDraftRepository {

    private static final String SELECT_COLUMNS = """
            SELECT encounter_id, author_id, priority, mode_of_ambulation, form_state::text AS form_state, updated_at
            FROM triage_assessment_draft
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTriageAssessmentDraftRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TriageAssessmentDraft upsert(UUID encounterId, UUID authorId, TriagePriority priority,
                                         ModeOfAmbulation modeOfAmbulation, String formStateJson) {
        String sql = """
                INSERT INTO triage_assessment_draft (encounter_id, author_id, priority, mode_of_ambulation, form_state, updated_at)
                VALUES (:encounterId, :authorId, :priority, :modeOfAmbulation, CAST(:formState AS JSONB), now())
                ON CONFLICT (encounter_id, author_id) DO UPDATE SET
                    priority = EXCLUDED.priority,
                    mode_of_ambulation = EXCLUDED.mode_of_ambulation,
                    form_state = EXCLUDED.form_state,
                    updated_at = now()
                RETURNING encounter_id, author_id, priority, mode_of_ambulation, form_state::text AS form_state, updated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("encounterId", encounterId)
                .addValue("authorId", authorId)
                .addValue("priority", priority == null ? null : priority.name())
                .addValue("modeOfAmbulation", modeOfAmbulation == null ? null : modeOfAmbulation.name())
                .addValue("formState", formStateJson == null ? "{}" : formStateJson);
        return jdbcTemplate.queryForObject(sql, params, this::mapRow);
    }

    @Override
    public Optional<TriageAssessmentDraft> findByEncounterAndAuthor(UUID encounterId, UUID authorId) {
        List<TriageAssessmentDraft> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId AND author_id = :authorId",
                new MapSqlParameterSource().addValue("encounterId", encounterId).addValue("authorId", authorId),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<TriageAssessmentDraft> findLatestByEncounterIds(Collection<UUID> encounterIds) {
        if (encounterIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT DISTINCT ON (encounter_id) encounter_id, author_id, priority, mode_of_ambulation, "
                + "form_state::text AS form_state, updated_at FROM triage_assessment_draft "
                + "WHERE encounter_id IN (:encounterIds) ORDER BY encounter_id, updated_at DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("encounterIds", encounterIds), this::mapRow);
    }

    private TriageAssessmentDraft mapRow(ResultSet rs, int rowNum) throws SQLException {
        String priority = rs.getString("priority");
        String modeOfAmbulation = rs.getString("mode_of_ambulation");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new TriageAssessmentDraft(
                (UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("author_id"),
                priority == null ? null : TriagePriority.valueOf(priority),
                modeOfAmbulation == null ? null : ModeOfAmbulation.valueOf(modeOfAmbulation),
                rs.getString("form_state"),
                updatedAt.toInstant());
    }
}
