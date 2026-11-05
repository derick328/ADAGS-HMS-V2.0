package com.adags.modules.referencedata.infrastructure;

import com.adags.modules.referencedata.domain.Icd10Code;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcIcd10CodeRepository implements Icd10CodeRepository {

    private static final String SELECT_COLUMNS = """
            SELECT code, title, chapter, block, parent_code, icd10_release, is_billable, is_active, created_at, is_selectable, entry_type
            FROM icd10_code
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcIcd10CodeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Icd10Code> findByCode(String code) {
        List<Icd10Code> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE code = :code",
                new MapSqlParameterSource("code", code),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public boolean upsert(Icd10Code code) {
        // is_active is deliberately absent from DO UPDATE SET — see
        // Icd10CodeRepository.upsert's contract: a re-import must never
        // silently reactivate a code the clinical lead retired.
        String sql = """
                INSERT INTO icd10_code (code, title, chapter, block, parent_code, icd10_release, is_billable)
                VALUES (:code, :title, :chapter, :block, :parentCode, :icd10Release, :isBillable)
                ON CONFLICT (code) DO UPDATE SET
                    title = EXCLUDED.title,
                    chapter = EXCLUDED.chapter,
                    block = EXCLUDED.block,
                    parent_code = EXCLUDED.parent_code,
                    icd10_release = EXCLUDED.icd10_release
                RETURNING (xmax = 0) AS inserted
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code.code())
                .addValue("title", code.title())
                .addValue("chapter", code.chapter(), Types.VARCHAR)
                .addValue("block", code.block(), Types.VARCHAR)
                .addValue("parentCode", code.parentCode(), Types.VARCHAR)
                .addValue("icd10Release", code.icd10Release())
                .addValue("isBillable", code.isBillable());

        Boolean inserted = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(inserted);
    }

    @Override
    public ImportStatus upsertWhoRow(String code, String title, String chapter, String block,
                                     String parentCode, String release, String entryType,
                                     String usageMarker, String longTitle, boolean selectable) {
        String sql = """
                INSERT INTO icd10_code (code, title, chapter, block, parent_code, icd10_release,
                                        entry_type, usage_marker, long_title, is_selectable, is_billable)
                VALUES (:code, :title, :chapter, :block, :parent, :release,
                        :entryType, :usage, :longTitle, :selectable, FALSE)
                ON CONFLICT (code) DO UPDATE SET
                    title = EXCLUDED.title, chapter = EXCLUDED.chapter, block = EXCLUDED.block,
                    parent_code = EXCLUDED.parent_code, icd10_release = EXCLUDED.icd10_release,
                    entry_type = EXCLUDED.entry_type, usage_marker = EXCLUDED.usage_marker,
                    long_title = EXCLUDED.long_title, is_selectable = EXCLUDED.is_selectable
                WHERE (icd10_code.title, icd10_code.chapter, icd10_code.block, icd10_code.parent_code,
                       icd10_code.icd10_release, icd10_code.entry_type, icd10_code.usage_marker,
                       icd10_code.long_title, icd10_code.is_selectable)
                   IS DISTINCT FROM
                      (EXCLUDED.title, EXCLUDED.chapter, EXCLUDED.block, EXCLUDED.parent_code,
                       EXCLUDED.icd10_release, EXCLUDED.entry_type, EXCLUDED.usage_marker,
                       EXCLUDED.long_title, EXCLUDED.is_selectable)
                RETURNING (xmax = 0) AS inserted
                """;
        var params = new MapSqlParameterSource()
                .addValue("code", code).addValue("title", title).addValue("chapter", chapter)
                .addValue("block", block).addValue("parent", parentCode).addValue("release", release)
                .addValue("entryType", entryType).addValue("usage", usageMarker)
                .addValue("longTitle", longTitle).addValue("selectable", selectable);
        List<Boolean> results = jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getBoolean("inserted"));
        if (results.isEmpty()) return ImportStatus.SKIPPED;
        return results.getFirst() ? ImportStatus.INSERTED : ImportStatus.UPDATED;
    }

    @Override
    public void deactivate(String code) {
        jdbcTemplate.update(
                "UPDATE icd10_code SET is_active = FALSE WHERE code = :code",
                new MapSqlParameterSource("code", code));
    }

    private Icd10Code mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Icd10Code(
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("chapter"),
                rs.getString("block"),
                rs.getString("parent_code"),
                rs.getString("icd10_release"),
                rs.getBoolean("is_billable"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("is_selectable"), rs.getString("entry_type"));
    }
}
