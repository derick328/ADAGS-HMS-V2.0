package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.domain.ItemClassificationChange;
import com.adags.modules.inventory.domain.ItemClassificationChangeRepository;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcItemClassificationChangeRepository implements ItemClassificationChangeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcItemClassificationChangeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ItemClassificationChange insert(ItemClassificationChange change) {
        String sql = """
                INSERT INTO item_classification_change (id, item_id, previous_schedule, new_schedule, reason, changed_by)
                VALUES (COALESCE(:id, gen_random_uuid()), :itemId, :previousSchedule, :newSchedule, :reason, :changedBy)
                RETURNING id, changed_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", change.id(), Types.OTHER)
                .addValue("itemId", change.itemId())
                .addValue("previousSchedule", change.previousSchedule().name())
                .addValue("newSchedule", change.newSchedule().name())
                .addValue("reason", change.reason())
                .addValue("changedBy", change.changedBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new ItemClassificationChange(
                (UUID) rs.getObject("id"), change.itemId(), change.previousSchedule(), change.newSchedule(),
                change.reason(), change.changedBy(), rs.getTimestamp("changed_at").toInstant()));
    }

    @Override
    public List<ItemClassificationChange> findControlledReleases() {
        String sql = """
                SELECT id, item_id, previous_schedule, new_schedule, reason, changed_by, changed_at
                FROM item_classification_change
                WHERE previous_schedule = 'CONTROLLED' AND new_schedule = 'PRESCRIPTION_ONLY'
                ORDER BY changed_at DESC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), this::mapRow);
    }

    private ItemClassificationChange mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ItemClassificationChange(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("item_id"),
                RegulatorySchedule.valueOf(rs.getString("previous_schedule")),
                RegulatorySchedule.valueOf(rs.getString("new_schedule")),
                rs.getString("reason"),
                (UUID) rs.getObject("changed_by"),
                rs.getTimestamp("changed_at").toInstant());
    }
}
