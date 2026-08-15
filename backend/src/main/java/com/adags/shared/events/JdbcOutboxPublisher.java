package com.adags.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

// Participates in the caller's ambient @Transactional — that is what makes
// this a transactional outbox rather than a fire-and-forget publish that a
// crash between commit and delivery could lose.
@Component
public class JdbcOutboxPublisher implements DomainEventPublisher {

    private static final String INSERT_SQL = """
            INSERT INTO outbox_event (event_type, payload)
            VALUES (:eventType, CAST(:payload AS JSONB))
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOutboxPublisher(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update(INSERT_SQL, new MapSqlParameterSource()
                    .addValue("eventType", eventType)
                    .addValue("payload", json));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize domain event " + eventType, e);
        }
    }
}
