package com.adags.modules.surgery.infrastructure;

import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTheatreRepository implements TheatreRepository {

    private static final String SELECT_COLUMNS = "SELECT id, facility_id, name, active, created_at FROM theatre";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTheatreRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Theatre insert(Theatre theatre) {
        String sql = """
                INSERT INTO theatre (facility_id, name, active)
                VALUES (:facilityId, :name, :active)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("facilityId", theatre.facilityId())
                .addValue("name", theatre.name())
                .addValue("active", theatre.active());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Theatre(
                (UUID) rs.getObject("id"), theatre.facilityId(), theatre.name(), theatre.active(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<Theatre> findById(UUID id) {
        List<Theatre> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Theatre> findByFacilityId(UUID facilityId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE facility_id = :facilityId AND active = TRUE ORDER BY name",
                new MapSqlParameterSource("facilityId", facilityId), this::mapRow);
    }

    private Theatre mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Theatre(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("facility_id"),
                rs.getString("name"),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant());
    }
}
