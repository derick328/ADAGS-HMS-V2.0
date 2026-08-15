package com.adags.modules.audit.infrastructure;

import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class JdbcAuditRecorder implements AuditRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO audit_log (actor_user_id, action, resource_type, resource_id, patient_id, is_break_glass, justification)
            VALUES (:actorUserId, 'BREAK_GLASS_ACCESS', :resourceType, :resourceId, :patientId, TRUE, :justification)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DomainEventPublisher eventPublisher;

    public JdbcAuditRecorder(NamedParameterJdbcTemplate jdbcTemplate, DomainEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void recordBreakGlass(UUID actorUserId, UUID patientId, String resourceType, UUID resourceId, String justification) {
        jdbcTemplate.update(INSERT_SQL, new MapSqlParameterSource()
                .addValue("actorUserId", actorUserId)
                .addValue("resourceType", resourceType)
                .addValue("resourceId", resourceId)
                .addValue("patientId", patientId)
                .addValue("justification", justification));

        // Alerts CLINICAL_LEAD, never another admin — root CLAUDE.md "Authorisation".
        eventPublisher.publish("BreakGlassAccessRecorded", Map.of(
                "actorUserId", actorUserId.toString(),
                "patientId", patientId.toString(),
                "resourceType", resourceType,
                "resourceId", resourceId == null ? "" : resourceId.toString(),
                "justification", justification
        ));
    }
}
