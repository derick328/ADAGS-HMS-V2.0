package com.adags.shared.numbering.infrastructure;

import com.adags.shared.numbering.GaplessNumberAllocator;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JdbcGaplessNumberAllocator implements GaplessNumberAllocator {

    // The UPSERT is the lock: two concurrent callers for the same
    // (sequence_name, facility_id, year) serialise on this row exactly the
    // way a SELECT ... FOR UPDATE would, and the loser of the race simply
    // waits for the winner's commit rather than reading a stale last_value.
    private static final String ALLOCATE_SQL = """
            INSERT INTO sequence_counter (sequence_name, facility_id, year, last_value)
            VALUES (:sequenceName, :facilityId, :year, 1)
            ON CONFLICT (sequence_name, facility_id, year)
            DO UPDATE SET last_value = sequence_counter.last_value + 1
            RETURNING last_value
            """;

    private static final String FACILITY_CODE_SQL = "SELECT code FROM facility WHERE id = :facilityId";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcGaplessNumberAllocator(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String allocate(String sequenceName, UUID facilityId, int year, int padWidth) {
        long nextValue = jdbcTemplate.queryForObject(ALLOCATE_SQL, new MapSqlParameterSource()
                .addValue("sequenceName", sequenceName)
                .addValue("facilityId", facilityId)
                .addValue("year", year), Long.class);

        String facilityCode = jdbcTemplate.queryForObject(FACILITY_CODE_SQL,
                new MapSqlParameterSource("facilityId", facilityId), String.class);

        return facilityCode + "-" + year + "-" + String.format("%0" + padWidth + "d", nextValue);
    }
}
