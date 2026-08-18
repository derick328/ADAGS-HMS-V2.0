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
            SELECT code, title, chapter, block, parent_code, icd10_release, is_billable, is_active, created_at
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
                    icd10_release = EXCLUDED.icd10_release,
                    is_billable = EXCLUDED.is_billable
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
                rs.getTimestamp("created_at").toInstant());
    }
}
