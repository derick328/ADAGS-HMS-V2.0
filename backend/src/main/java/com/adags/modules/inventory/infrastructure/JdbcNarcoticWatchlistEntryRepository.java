package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.NarcoticWatchlistEntry;
import com.adags.modules.inventory.domain.NarcoticWatchlistEntryRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcNarcoticWatchlistEntryRepository implements NarcoticWatchlistEntryRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, inn_name, atc_prefix, source_document, source_date, added_by, added_at FROM narcotic_watchlist_entry";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcNarcoticWatchlistEntryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public NarcoticWatchlistEntry insert(NarcoticWatchlistEntry entry) {
        String sql = """
                INSERT INTO narcotic_watchlist_entry (id, inn_name, atc_prefix, source_document, source_date, added_by)
                VALUES (COALESCE(:id, gen_random_uuid()), :innName, :atcPrefix, :sourceDocument, :sourceDate, :addedBy)
                RETURNING id, added_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.id(), Types.OTHER)
                .addValue("innName", entry.innName())
                .addValue("atcPrefix", entry.atcPrefix(), Types.VARCHAR)
                .addValue("sourceDocument", entry.sourceDocument())
                .addValue("sourceDate", entry.sourceDate())
                .addValue("addedBy", entry.addedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new NarcoticWatchlistEntry(
                (UUID) rs.getObject("id"), entry.innName(), entry.atcPrefix(), entry.sourceDocument(),
                entry.sourceDate(), entry.addedBy(), rs.getTimestamp("added_at").toInstant()));
    }

    @Override
    public List<NarcoticWatchlistEntry> findAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY inn_name", new MapSqlParameterSource(), this::mapRow);
    }

    private NarcoticWatchlistEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NarcoticWatchlistEntry(
                (UUID) rs.getObject("id"),
                rs.getString("inn_name"),
                rs.getString("atc_prefix"),
                rs.getString("source_document"),
                rs.getDate("source_date").toLocalDate(),
                (UUID) rs.getObject("added_by"),
                rs.getTimestamp("added_at").toInstant());
    }
}
