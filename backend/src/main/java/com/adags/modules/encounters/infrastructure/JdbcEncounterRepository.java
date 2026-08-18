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
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                UPDATE encounter SET assigned_provider_id = :assignedProviderId, type = :type, version = version + 1
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
