package com.adags.modules.referencedata.infrastructure;

import com.adags.modules.referencedata.domain.Icd10Synonym;
import com.adags.modules.referencedata.domain.Icd10SynonymRepository;
import com.adags.modules.referencedata.domain.SynonymStatus;
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
public class JdbcIcd10SynonymRepository implements Icd10SynonymRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, code, term, language, status, proposed_by, proposed_at,
                   reviewed_by, reviewed_at, review_note, is_active
            FROM icd10_synonym
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcIcd10SynonymRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Icd10Synonym insert(Icd10Synonym synonym) {
        String sql = """
                INSERT INTO icd10_synonym (code, term, language, status, proposed_by)
                VALUES (:code, :term, :language, :status, :proposedBy)
                RETURNING id, proposed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", synonym.code())
                .addValue("term", synonym.term())
                .addValue("language", synonym.language())
                .addValue("status", synonym.status().name())
                .addValue("proposedBy", synonym.proposedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Icd10Synonym(
                (UUID) rs.getObject("id"), synonym.code(), synonym.term(), synonym.language(), synonym.status(),
                synonym.proposedBy(), rs.getTimestamp("proposed_at").toInstant(),
                null, null, null, true));
    }

    @Override
    public Optional<Icd10Synonym> findById(UUID id) {
        List<Icd10Synonym> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Icd10Synonym> findByStatus(SynonymStatus status) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = :status ORDER BY proposed_at",
                new MapSqlParameterSource("status", status.name()),
                this::mapRow);
    }

    @Override
    public void review(UUID id, SynonymStatus decision, UUID reviewedBy, Instant reviewedAt, String reviewNote) {
        jdbcTemplate.update("""
                        UPDATE icd10_synonym
                        SET status = :status, reviewed_by = :reviewedBy, reviewed_at = :reviewedAt, review_note = :reviewNote
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("status", decision.name())
                        .addValue("reviewedBy", reviewedBy)
                        .addValue("reviewedAt", Timestamp.from(reviewedAt))
                        .addValue("reviewNote", reviewNote, Types.VARCHAR)
                        .addValue("id", id));
    }

    private Icd10Synonym mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        return new Icd10Synonym(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("term"),
                rs.getString("language"),
                SynonymStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("proposed_by"),
                rs.getTimestamp("proposed_at").toInstant(),
                (UUID) rs.getObject("reviewed_by"),
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getString("review_note"),
                rs.getBoolean("is_active"));
    }
}
