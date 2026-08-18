package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.VerificationLevel;
import com.adags.modules.laboratory.domain.VerificationState;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLabResultRepository implements LabResultRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, lab_order_id, specimen_id, entered_by, entered_at, released_at,
                   verification_state, verified_by, verified_at, verification_comment,
                   verification_level_applied, result_version_hash,
                   methodology, reagents_used_note, findings, interpretation, reference_range_note, conclusion,
                   machine_output_object_key, amends_id, amendment_reason, created_at
            FROM lab_result
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcLabResultRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LabResult insert(LabResult result) {
        String sql = """
                INSERT INTO lab_result (lab_order_id, specimen_id, entered_by, verification_state,
                                         verification_level_applied, methodology, reagents_used_note, findings,
                                         interpretation, reference_range_note, conclusion,
                                         machine_output_object_key, amends_id, amendment_reason)
                VALUES (:labOrderId, :specimenId, :enteredBy, :verificationState,
                        :verificationLevelApplied, :methodology, :reagentsUsedNote, :findings,
                        :interpretation, :referenceRangeNote, :conclusion,
                        :machineOutputObjectKey, :amendsId, :amendmentReason)
                RETURNING id, entered_at, released_at, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("labOrderId", result.labOrderId())
                .addValue("specimenId", result.specimenId())
                .addValue("enteredBy", result.enteredBy())
                .addValue("verificationState", result.verificationState().name())
                .addValue("verificationLevelApplied", result.verificationLevelApplied().name())
                .addValue("methodology", result.methodology())
                .addValue("reagentsUsedNote", result.reagentsUsedNote())
                .addValue("findings", result.findings())
                .addValue("interpretation", result.interpretation())
                .addValue("referenceRangeNote", result.referenceRangeNote())
                .addValue("conclusion", result.conclusion())
                .addValue("machineOutputObjectKey", result.machineOutputObjectKey())
                .addValue("amendsId", result.amendsId(), Types.OTHER)
                .addValue("amendmentReason", result.amendmentReason());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new LabResult(
                (UUID) rs.getObject("id"), result.labOrderId(), result.specimenId(), result.enteredBy(),
                rs.getTimestamp("entered_at").toInstant(), rs.getTimestamp("released_at").toInstant(),
                result.verificationState(), null, null, null, result.verificationLevelApplied(), null,
                result.methodology(), result.reagentsUsedNote(), result.findings(), result.interpretation(),
                result.referenceRangeNote(), result.conclusion(), result.machineOutputObjectKey(),
                result.amendsId(), result.amendmentReason(), rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<LabResult> findById(UUID id) {
        List<LabResult> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<LabResult> findBySpecimenId(UUID specimenId) {
        String sql = SELECT_COLUMNS + " WHERE specimen_id = :specimenId ORDER BY entered_at DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("specimenId", specimenId), this::mapRow);
    }

    @Override
    public boolean verify(UUID resultId, UUID verifiedBy, String verificationComment, String resultVersionHash) {
        int updated = jdbcTemplate.update("""
                        UPDATE lab_result
                        SET verified_by = :verifiedBy, verified_at = now(), verification_comment = :verificationComment,
                            result_version_hash = :resultVersionHash, verification_state = 'VERIFIED'
                        WHERE id = :id AND verification_state = 'PENDING'
                        """,
                new MapSqlParameterSource()
                        .addValue("id", resultId)
                        .addValue("verifiedBy", verifiedBy)
                        .addValue("verificationComment", verificationComment)
                        .addValue("resultVersionHash", resultVersionHash));
        return updated == 1;
    }

    @Override
    public void markAmended(UUID resultId) {
        jdbcTemplate.update(
                "UPDATE lab_result SET verification_state = 'AMENDED' WHERE id = :id",
                new MapSqlParameterSource("id", resultId));
    }

    private LabResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp verifiedAt = rs.getTimestamp("verified_at");
        Object verifiedBy = rs.getObject("verified_by");
        Object amendsId = rs.getObject("amends_id");
        return new LabResult(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("lab_order_id"),
                (UUID) rs.getObject("specimen_id"),
                (UUID) rs.getObject("entered_by"),
                rs.getTimestamp("entered_at").toInstant(),
                rs.getTimestamp("released_at").toInstant(),
                VerificationState.valueOf(rs.getString("verification_state")),
                verifiedBy == null ? null : (UUID) verifiedBy,
                verifiedAt == null ? null : verifiedAt.toInstant(),
                rs.getString("verification_comment"),
                VerificationLevel.valueOf(rs.getString("verification_level_applied")),
                rs.getString("result_version_hash"),
                rs.getString("methodology"),
                rs.getString("reagents_used_note"),
                rs.getString("findings"),
                rs.getString("interpretation"),
                rs.getString("reference_range_note"),
                rs.getString("conclusion"),
                rs.getString("machine_output_object_key"),
                amendsId == null ? null : (UUID) amendsId,
                rs.getString("amendment_reason"),
                rs.getTimestamp("created_at").toInstant());
    }
}
