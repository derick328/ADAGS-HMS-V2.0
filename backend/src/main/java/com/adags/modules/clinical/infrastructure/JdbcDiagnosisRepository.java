package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.domain.Diagnosis;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.clinical.domain.DiagnosisRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcDiagnosisRepository implements DiagnosisRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, consultation_record_id, icd10_code, icd10_title_snapshot, free_text_clarifier,
                   certainty, is_primary, diagnosed_by, diagnosed_at, amends_id, amendment_reason
            FROM diagnosis
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDiagnosisRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Diagnosis insert(Diagnosis diagnosis) {
        String sql = """
                INSERT INTO diagnosis (encounter_id, consultation_record_id, icd10_code, icd10_title_snapshot,
                                        free_text_clarifier, certainty, is_primary, diagnosed_by, amends_id, amendment_reason)
                VALUES (:encounterId, :consultationRecordId, :icd10Code, :icd10TitleSnapshot,
                        :freeTextClarifier, :certainty, :isPrimary, :diagnosedBy, :amendsId, :amendmentReason)
                RETURNING id, diagnosed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("encounterId", diagnosis.encounterId())
                .addValue("consultationRecordId", diagnosis.consultationRecordId())
                .addValue("icd10Code", diagnosis.icd10Code())
                .addValue("icd10TitleSnapshot", diagnosis.icd10TitleSnapshot())
                .addValue("freeTextClarifier", diagnosis.freeTextClarifier(), Types.VARCHAR)
                .addValue("certainty", diagnosis.certainty().name())
                .addValue("isPrimary", diagnosis.isPrimary())
                .addValue("diagnosedBy", diagnosis.diagnosedBy())
                .addValue("amendsId", diagnosis.amendsId(), Types.OTHER)
                .addValue("amendmentReason", diagnosis.amendmentReason(), Types.VARCHAR);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Diagnosis(
                (UUID) rs.getObject("id"), diagnosis.encounterId(), diagnosis.consultationRecordId(),
                diagnosis.icd10Code(), diagnosis.icd10TitleSnapshot(), diagnosis.freeTextClarifier(),
                diagnosis.certainty(), diagnosis.isPrimary(), diagnosis.diagnosedBy(),
                rs.getTimestamp("diagnosed_at").toInstant(), diagnosis.amendsId(), diagnosis.amendmentReason()));
    }

    @Override
    public List<Diagnosis> findByEncounterId(UUID encounterId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId ORDER BY diagnosed_at DESC",
                new MapSqlParameterSource("encounterId", encounterId),
                this::mapRow);
    }

    @Override
    public boolean existsPrimaryForEncounter(UUID encounterId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM diagnosis WHERE encounter_id = :encounterId AND is_primary)",
                new MapSqlParameterSource("encounterId", encounterId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private Diagnosis mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Diagnosis(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                (UUID) rs.getObject("consultation_record_id"),
                rs.getString("icd10_code"),
                rs.getString("icd10_title_snapshot"),
                rs.getString("free_text_clarifier"),
                DiagnosisCertainty.valueOf(rs.getString("certainty")),
                rs.getBoolean("is_primary"),
                (UUID) rs.getObject("diagnosed_by"),
                rs.getTimestamp("diagnosed_at").toInstant(),
                (UUID) rs.getObject("amends_id"),
                rs.getString("amendment_reason"));
    }
}
