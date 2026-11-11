package com.adags.modules.encounters.infrastructure;

import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterEvent;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.EncounterType;
import com.adags.modules.encounters.domain.StaleEncounterException;
import com.adags.shared.kernel.PayerType;
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
import java.util.stream.Collectors;

@Repository
public class JdbcEncounterRepository implements EncounterRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, patient_id, facility_id, department_id, appointment_id, assigned_provider_id, type, state,
                   payer_type, version, created_at, created_by
            FROM encounter
            """;

    // Mirrors the partial unique index in V8__encounters_module.sql — kept
    // as the literal state list (not EncounterState.isTerminal() filtered
    // in Java) so this query can actually use that index rather than a
    // sequential scan.
    private static final String NON_TERMINAL_STATES = """
            'LEFT_WITHOUT_BEING_SEEN', 'ABANDONED_UNPAID', 'CANCELLED_IN_ERROR',
            'REFERRED_OUT', 'DECEASED', 'CLOSED', 'DISCHARGED'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcEncounterRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Encounter> findById(UUID id) {
        List<Encounter> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Encounter> findOpenByPatientId(UUID patientId) {
        List<Encounter> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE patient_id = :patientId AND state NOT IN (" + NON_TERMINAL_STATES + ")",
                new MapSqlParameterSource("patientId", patientId),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Encounter> findOpenByPatientIdForUpdate(UUID patientId) {
        List<Encounter> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE patient_id = :patientId AND state NOT IN (" + NON_TERMINAL_STATES + ") FOR UPDATE",
                new MapSqlParameterSource("patientId", patientId),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Encounter> findByPatientId(UUID patientId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE patient_id IN (SELECT alias_id FROM patient_canonical_identity WHERE patient_id = (SELECT patient_id FROM patient_canonical_identity WHERE alias_id = :patientId)) ORDER BY created_at DESC",
                new MapSqlParameterSource("patientId", patientId),
                this::mapRow);
    }

    @Override
    public List<Encounter> search(List<EncounterState> states, boolean excludeTerminal, Instant createdFrom, Instant createdTo) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (states != null && !states.isEmpty()) {
            sql.append(" AND state IN (:states)");
            params.addValue("states", states.stream().map(Enum::name).collect(Collectors.toList()));
        }
        if (excludeTerminal) {
            sql.append(" AND state NOT IN (").append(NON_TERMINAL_STATES).append(")");
        }
        if (createdFrom != null) {
            sql.append(" AND created_at >= :createdFrom");
            params.addValue("createdFrom", Timestamp.from(createdFrom));
        }
        if (createdTo != null) {
            sql.append(" AND created_at < :createdTo");
            params.addValue("createdTo", Timestamp.from(createdTo));
        }
        sql.append(" ORDER BY created_at");

        return jdbcTemplate.query(sql.toString(), params, this::mapRow);
    }

    @Override
    public int countByAssignedProviderAndState(UUID assignedProviderId, EncounterState state) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM encounter WHERE assigned_provider_id = :assignedProviderId AND state = :state",
                new MapSqlParameterSource()
                        .addValue("assignedProviderId", assignedProviderId)
                        .addValue("state", state.name()),
                Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public Encounter insert(Encounter encounter) {
        String sql = """
                INSERT INTO encounter (patient_id, facility_id, department_id, appointment_id, type,
                                        state, payer_type, created_by)
                VALUES (:patientId, :facilityId, :departmentId, :appointmentId, :type,
                        :state, :payerType, :createdBy)
                RETURNING id, version, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("patientId", encounter.patientId())
                .addValue("facilityId", encounter.facilityId())
                .addValue("departmentId", encounter.departmentId())
                .addValue("appointmentId", encounter.appointmentId(), Types.OTHER)
                .addValue("type", encounter.type().name())
                .addValue("state", encounter.state().name())
                .addValue("payerType", encounter.payerType().name())
                .addValue("createdBy", encounter.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new Encounter(
                (UUID) rs.getObject("id"),
                encounter.patientId(),
                encounter.facilityId(),
                encounter.departmentId(),
                encounter.appointmentId(),
                null,
                encounter.type(),
                encounter.state(),
                encounter.payerType(),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(),
                encounter.createdBy()));
    }

    @Override
    public void updateState(UUID encounterId, EncounterState newState) {
        jdbcTemplate.update(
                "UPDATE encounter SET state = :state, version = version + 1 WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("state", newState.name())
                        .addValue("id", encounterId));
    }

    @Override
    public int assignProviderAndType(UUID encounterId, UUID assignedProviderId, EncounterType type, int expectedVersion) {
        String sql = """
                UPDATE encounter SET assigned_provider_id = :assignedProviderId,
                    department_id = COALESCE(department_id,
                        (SELECT ds.department_id FROM department_staff ds
                         JOIN department d ON d.id = ds.department_id
                         WHERE ds.user_id = :assignedProviderId AND d.facility_id = encounter.facility_id
                           AND d.active AND d.consultation_enabled
                         ORDER BY ds.is_primary DESC, CASE WHEN d.code = 'GENERAL' THEN 1 ELSE 0 END, d.code
                         LIMIT 1),
                        (SELECT d.id FROM department d WHERE d.facility_id = encounter.facility_id
                           AND d.code = 'GENERAL' AND d.active LIMIT 1)),
                    type = :type, version = version + 1
                WHERE id = :id AND version = :expectedVersion
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("assignedProviderId", assignedProviderId)
                .addValue("type", type.name())
                .addValue("id", encounterId)
                .addValue("expectedVersion", expectedVersion));
        if (updated == 0) {
            throw new StaleEncounterException(encounterId, expectedVersion);
        }
        return expectedVersion + 1;
    }

    @Override
    public void insertEvent(EncounterEvent event) {
        String sql = """
                INSERT INTO encounter_event (encounter_id, from_state, to_state, actor_user_id, reason)
                VALUES (:encounterId, :fromState, :toState, :actorUserId, :reason)
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("encounterId", event.encounterId())
                .addValue("fromState", event.fromState() == null ? null : event.fromState().name(), Types.VARCHAR)
                .addValue("toState", event.toState().name())
                .addValue("actorUserId", event.actorUserId())
                .addValue("reason", event.reason(), Types.VARCHAR));
    }

    private Encounter mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Encounter(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("facility_id"),
                (UUID) rs.getObject("department_id"),
                (UUID) rs.getObject("appointment_id"),
                (UUID) rs.getObject("assigned_provider_id"),
                EncounterType.valueOf(rs.getString("type")),
                EncounterState.valueOf(rs.getString("state")),
                PayerType.valueOf(rs.getString("payer_type")),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(),
                (UUID) rs.getObject("created_by"));
    }
}
