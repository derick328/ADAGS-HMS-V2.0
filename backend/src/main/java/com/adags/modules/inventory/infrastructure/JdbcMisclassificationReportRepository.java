package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.MisclassificationCandidate;
import com.adags.modules.inventory.domain.MisclassificationReportRepository;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcMisclassificationReportRepository implements MisclassificationReportRepository {

    // Best match per item (DISTINCT ON item.id, highest similarity first),
    // then re-sorted overall by score — 12-pharmacist.md §10.6: "Trigram
    // match on INN... False positives are expected and cheap; a missed
    // controlled drug is not," so this deliberately does not restrict to
    // exact or near-exact matches beyond the tunable threshold.
    private static final String FIND_CANDIDATES_SQL = """
            WITH matches AS (
                SELECT DISTINCT ON (i.id)
                       i.id AS item_id, i.code, i.name, i.regulatory_schedule,
                       w.inn_name AS matched_inn_name,
                       similarity(i.name, w.inn_name) AS score
                FROM item i
                JOIN narcotic_watchlist_entry w ON similarity(i.name, w.inn_name) > :threshold
                WHERE i.regulatory_schedule IN ('UNCLASSIFIED', 'PRESCRIPTION_ONLY')
                ORDER BY i.id, score DESC
            )
            SELECT * FROM matches ORDER BY score DESC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMisclassificationReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MisclassificationCandidate> findCandidates(double threshold) {
        return jdbcTemplate.query(FIND_CANDIDATES_SQL,
                new MapSqlParameterSource("threshold", threshold), this::mapRow);
    }

    private MisclassificationCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new MisclassificationCandidate(
                (UUID) rs.getObject("item_id"),
                rs.getString("code"),
                rs.getString("name"),
                RegulatorySchedule.valueOf(rs.getString("regulatory_schedule")),
                rs.getString("matched_inn_name"),
                rs.getDouble("score"));
    }
}
