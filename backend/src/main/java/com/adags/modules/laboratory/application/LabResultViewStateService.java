package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.LabResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LabResultViewStateService {
    private final NamedParameterJdbcTemplate jdbc;
    public LabResultViewStateService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<UUID> unreadOrderIds(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT lr.lab_order_id FROM lab_result lr
                JOIN lab_order lo ON lo.id = lr.lab_order_id
                LEFT JOIN lab_result_view_state seen ON seen.result_id = lr.id AND seen.user_id = :userId
                WHERE lo.ordered_by = :userId AND lr.released_at IS NOT NULL AND seen.result_id IS NULL
                """, new MapSqlParameterSource("userId", userId), (rs, row) -> (UUID) rs.getObject(1));
    }

    @Transactional
    public void markViewed(UUID userId, List<LabResult> releasedResults) {
        for (LabResult result : releasedResults) {
            jdbc.update("""
                    INSERT INTO lab_result_view_state (user_id, result_id) VALUES (:userId, :resultId)
                    ON CONFLICT (user_id, result_id) DO NOTHING
                    """, new MapSqlParameterSource().addValue("userId", userId).addValue("resultId", result.id()));
        }
    }
}
