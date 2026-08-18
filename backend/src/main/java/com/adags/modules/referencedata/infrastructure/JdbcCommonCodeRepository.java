package com.adags.modules.referencedata.infrastructure;

import com.adags.modules.referencedata.domain.CommonCode;
import com.adags.modules.referencedata.domain.CommonCodeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Types;
import java.util.UUID;

@Repository
public class JdbcCommonCodeRepository implements CommonCodeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCommonCodeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CommonCode insert(CommonCode commonCode) {
        String sql = """
                INSERT INTO common_code (code, department_id, sort_rank, added_by)
                VALUES (:code, :departmentId, :sortRank, :addedBy)
                RETURNING id, added_at
                """;
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", commonCode.code())
                .addValue("departmentId", commonCode.departmentId(), Types.OTHER)
                .addValue("sortRank", commonCode.sortRank())
                .addValue("addedBy", commonCode.addedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new CommonCode(
                (UUID) rs.getObject("id"),
                commonCode.code(),
                commonCode.departmentId(),
                commonCode.sortRank(),
                commonCode.addedBy(),
                rs.getTimestamp("added_at").toInstant(),
                true));
    }
}
