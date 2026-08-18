package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.BedStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBedRepository implements BedRepository {

    private static final String SELECT_COLUMNS = "SELECT id, ward_id, label, bed_type, status, created_at FROM bed";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcBedRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Bed> findById(UUID id) {
        List<Bed> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Bed> findByWardId(UUID wardId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE ward_id = :wardId ORDER BY label",
                new MapSqlParameterSource("wardId", wardId), this::mapRow);
    }

    @Override
    public List<Bed> findFreeByWardId(UUID wardId) {
        String sql = SELECT_COLUMNS + """
                 WHERE ward_id = :wardId AND status = 'AVAILABLE'
                   AND NOT EXISTS (
                       SELECT 1 FROM bed_assignment ba
                        WHERE ba.bed_id = bed.id AND ba.released_at IS NULL
                   )
                 ORDER BY label
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("wardId", wardId), this::mapRow);
    }

    @Override
    public Bed insert(Bed bed) {
        String sql = """
                INSERT INTO bed (ward_id, label, bed_type, status)
                VALUES (:wardId, :label, :bedType, :status)
                RETURNING id, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("wardId", bed.wardId())
                .addValue("label", bed.label())
                .addValue("bedType", bed.bedType())
                .addValue("status", bed.status().name());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Bed(
                (UUID) rs.getObject("id"), bed.wardId(), bed.label(), bed.bedType(), bed.status(),
                rs.getTimestamp("created_at").toInstant()));
    }

    private Bed mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Bed(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("ward_id"),
                rs.getString("label"),
                rs.getString("bed_type"),
                BedStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
