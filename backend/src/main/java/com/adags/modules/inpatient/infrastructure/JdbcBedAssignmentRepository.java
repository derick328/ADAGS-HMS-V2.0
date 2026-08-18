package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.domain.BedAssignment;
import com.adags.modules.inpatient.domain.BedAssignmentRepository;
import com.adags.modules.inpatient.domain.BedNotAvailableException;
import com.adags.modules.inpatient.domain.PlacementReason;
import org.springframework.dao.DuplicateKeyException;
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
public class JdbcBedAssignmentRepository implements BedAssignmentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, admission_id, bed_id, assigned_at, released_at, assigned_by,
                   placed_above_ordered_class, placement_reason
            FROM bed_assignment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcBedAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BedAssignment> findActiveByAdmissionId(UUID admissionId) {
        List<BedAssignment> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE admission_id = :admissionId AND released_at IS NULL",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<BedAssignment> findByAdmissionId(UUID admissionId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE admission_id = :admissionId ORDER BY assigned_at DESC",
                new MapSqlParameterSource("admissionId", admissionId), this::mapRow);
    }

    @Override
    public BedAssignment insert(BedAssignment assignment) {
        String sql = """
                INSERT INTO bed_assignment (admission_id, bed_id, assigned_by, placed_above_ordered_class, placement_reason)
                VALUES (:admissionId, :bedId, :assignedBy, :placedAboveOrderedClass, :placementReason)
                RETURNING id, assigned_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("admissionId", assignment.admissionId())
                .addValue("bedId", assignment.bedId())
                .addValue("assignedBy", assignment.assignedBy())
                .addValue("placedAboveOrderedClass", assignment.placedAboveOrderedClass())
                .addValue("placementReason",
                        assignment.placementReason() == null ? null : assignment.placementReason().name(), Types.VARCHAR);

        try {
            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new BedAssignment(
                    (UUID) rs.getObject("id"), assignment.admissionId(), assignment.bedId(),
                    rs.getTimestamp("assigned_at").toInstant(), null, assignment.assignedBy(),
                    assignment.placedAboveOrderedClass(), assignment.placementReason()));
        } catch (DuplicateKeyException raceLost) {
            throw new BedNotAvailableException(assignment.bedId());
        }
    }

    @Override
    public void release(UUID bedAssignmentId, Instant releasedAt) {
        jdbcTemplate.update(
                "UPDATE bed_assignment SET released_at = :releasedAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("releasedAt", Timestamp.from(releasedAt))
                        .addValue("id", bedAssignmentId));
    }

    private BedAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp releasedAt = rs.getTimestamp("released_at");
        String placementReason = rs.getString("placement_reason");
        return new BedAssignment(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("admission_id"),
                (UUID) rs.getObject("bed_id"),
                rs.getTimestamp("assigned_at").toInstant(),
                releasedAt == null ? null : releasedAt.toInstant(),
                (UUID) rs.getObject("assigned_by"),
                rs.getBoolean("placed_above_ordered_class"),
                placementReason == null ? null : PlacementReason.valueOf(placementReason));
    }
}
