package com.adags.modules.referencedata.infrastructure;

import com.adags.modules.referencedata.domain.Icd10SearchRepository;
import com.adags.modules.referencedata.domain.Icd10SearchResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

// 10-clinical-reference-data.md §5.2's ranking formula as one query.
// Candidates are narrowed first (the `candidates` CTE: is_active,
// is_billable, and a code-prefix/title-prefix/trigram/synonym match) so the
// per-row scoring subqueries below only ever run over that narrowed set —
// §5.3: "Do not query 14,000 rows on every keystroke." Redis caching of
// common lists (§5.3) is deliberately not added yet: there is no curation
// endpoint in this phase to invalidate it on, and a cache with no
// invalidation trigger would silently serve stale common-list boosts,
// which is worse than the extra query.
@Repository
public class JdbcIcd10SearchRepository implements Icd10SearchRepository {

    // Loose enough to surface a typo'd query as a fuzzy match; the score
    // itself (similarity * 50) still ranks a near-exact hit far above a
    // marginal one — the threshold only decides candidacy, not ranking.
    private static final double TRIGRAM_THRESHOLD = 0.1;

    private static final String SQL = """
            WITH candidates AS (
                SELECT DISTINCT c.code
                FROM icd10_code c
                LEFT JOIN icd10_synonym s
                       ON s.code = c.code AND s.status = 'APPROVED' AND s.is_active
                WHERE c.is_active AND c.is_billable
                  AND (
                        c.code LIKE :codePrefix
                     OR c.title ILIKE :titlePrefix
                     OR similarity(c.title, :queryText) > :threshold
                     OR (s.id IS NOT NULL AND (s.term ILIKE :titlePrefix OR similarity(s.term, :queryText) > :threshold))
                  )
            )
            SELECT
                c.code,
                c.title,
                c.is_billable,
                (
                    (CASE WHEN c.code = :exactCode THEN 1000 ELSE 0 END)
                  + COALESCE((SELECT 500 - cc.sort_rank FROM common_code cc
                               WHERE cc.code = c.code AND cc.department_id = :departmentId AND cc.is_active
                               LIMIT 1), 0)
                  + COALESCE((SELECT 300 - cc.sort_rank FROM common_code cc
                               WHERE cc.code = c.code AND cc.department_id IS NULL AND cc.is_active
                               LIMIT 1), 0)
                  + (CASE WHEN EXISTS (
                         SELECT 1 FROM icd10_synonym s2
                         WHERE s2.code = c.code AND s2.status = 'APPROVED' AND s2.is_active
                           AND (s2.term ILIKE :titlePrefix OR similarity(s2.term, :queryText) > :threshold)
                       ) THEN 200 ELSE 0 END)
                  + (CASE WHEN c.title ILIKE :titlePrefix THEN 100 ELSE 0 END)
                  + (similarity(c.title, :queryText) * 50)
                  + (CASE WHEN EXISTS (
                         SELECT 1 FROM reference_search_log l
                         WHERE l.actor_user_id = :actorUserId AND l.catalogue_type = 'ICD10'
                           AND l.selected_id = c.code AND l.searched_at > now() - INTERVAL '90 days'
                       ) THEN 25 ELSE 0 END)
                ) AS score
            FROM icd10_code c
            WHERE c.code IN (SELECT code FROM candidates)
            ORDER BY score DESC, c.code ASC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcIcd10SearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Icd10SearchResult> search(String queryText, UUID departmentId, UUID actorUserId, int limit) {
        String trimmed = queryText.trim();
        String exactCode = trimmed.toUpperCase();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryText", trimmed)
                .addValue("exactCode", exactCode)
                .addValue("codePrefix", exactCode + "%")
                .addValue("titlePrefix", trimmed + "%")
                .addValue("threshold", TRIGRAM_THRESHOLD)
                .addValue("departmentId", departmentId, Types.OTHER)
                .addValue("actorUserId", actorUserId, Types.OTHER)
                .addValue("limit", limit);

        return jdbcTemplate.query(SQL, params, this::mapRow);
    }

    private Icd10SearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Icd10SearchResult(
                rs.getString("code"),
                rs.getString("title"),
                rs.getBoolean("is_billable"),
                rs.getDouble("score"));
    }
}
