package com.adags.modules.surgery.infrastructure;

import com.adags.modules.surgery.domain.CancellationReason;
import com.adags.modules.surgery.domain.SurgeryDisposition;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.modules.surgery.domain.SurgeryUrgency;
import com.adags.modules.surgery.domain.TheatreSlotNotAvailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSurgeryOrderRepository implements SurgeryOrderRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, encounter_id, procedure_service_item_code, procedure_name, surgery_type, urgency,
                   anesthesia_type, scheduled_date, time_slot, theatre_id, estimated_duration_minutes,
                   assigned_nurses::text AS assigned_nurses, pre_op_notes, consent_obtained, consent_document_key,
                   status, invoice_line_id, scheduled_by, scheduled_at, version,
                   disposition, disposition_recorded_by, disposition_recorded_at,
                   cancellation_reason, cancelled_by, cancelled_at, governance_escalated_at
            FROM surgery_order
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSurgeryOrderRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public SurgeryOrder insert(SurgeryOrder surgeryOrder) {
        String sql = """
                INSERT INTO surgery_order (
                    id, encounter_id, procedure_service_item_code, procedure_name, surgery_type, urgency,
                    anesthesia_type, scheduled_date, time_slot, theatre_id, estimated_duration_minutes,
                    assigned_nurses, pre_op_notes, consent_obtained, consent_document_key, status,
                    invoice_line_id, scheduled_by)
                VALUES (
                    :id, :encounterId, :procedureServiceItemCode, :procedureName, :surgeryType, :urgency,
                    :anesthesiaType, :scheduledDate, :timeSlot, :theatreId, :estimatedDurationMinutes,
                    CAST(:assignedNurses AS JSONB), :preOpNotes, :consentObtained, :consentDocumentKey, :status,
                    :invoiceLineId, :scheduledBy)
                RETURNING id, scheduled_at, version
                """;
        UUID id = surgeryOrder.id() == null ? UUID.randomUUID() : surgeryOrder.id();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("encounterId", surgeryOrder.encounterId())
                .addValue("procedureServiceItemCode", surgeryOrder.procedureServiceItemCode(), Types.VARCHAR)
                .addValue("procedureName", surgeryOrder.procedureName())
                .addValue("surgeryType", surgeryOrder.surgeryType())
                .addValue("urgency", surgeryOrder.urgency().name())
                .addValue("anesthesiaType", surgeryOrder.anesthesiaType())
                .addValue("scheduledDate", surgeryOrder.scheduledDate())
                .addValue("timeSlot", surgeryOrder.timeSlot())
                .addValue("theatreId", surgeryOrder.theatreId())
                .addValue("estimatedDurationMinutes", surgeryOrder.estimatedDurationMinutes())
                .addValue("assignedNurses", writeJson(surgeryOrder.assignedNurses()))
                .addValue("preOpNotes", surgeryOrder.preOpNotes())
                .addValue("consentObtained", surgeryOrder.consentObtained())
                .addValue("consentDocumentKey", surgeryOrder.consentDocumentKey())
                .addValue("status", surgeryOrder.status().name())
                .addValue("invoiceLineId", surgeryOrder.invoiceLineId(), Types.OTHER)
                .addValue("scheduledBy", surgeryOrder.scheduledBy(), Types.OTHER);

        try {
            return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> surgeryOrder.withId((UUID) rs.getObject("id")));
        } catch (DuplicateKeyException raceLost) {
            throw new TheatreSlotNotAvailableException(
                    surgeryOrder.theatreId(), surgeryOrder.scheduledDate(), surgeryOrder.timeSlot());
        }
    }

    @Override
    public Optional<SurgeryOrder> findById(UUID id) {
        List<SurgeryOrder> results = jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = :id", new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<SurgeryOrder> findByEncounterId(UUID encounterId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE encounter_id = :encounterId ORDER BY scheduled_at DESC",
                new MapSqlParameterSource("encounterId", encounterId), this::mapRow);
    }

    @Override
    public List<SurgeryOrder> findActive() {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY scheduled_date, time_slot",
                new MapSqlParameterSource(), this::mapRow);
    }

    @Override
    public boolean transitionStatus(UUID id, SurgeryOrderStatus from, SurgeryOrderStatus to) {
        int updated = jdbcTemplate.update(
                "UPDATE surgery_order SET status = :to, version = version + 1 WHERE id = :id AND status = :from",
                new MapSqlParameterSource()
                        .addValue("to", to.name())
                        .addValue("id", id)
                        .addValue("from", from.name()));
        return updated > 0;
    }

    @Override
    public boolean revokeConsent(UUID id) {
        int updated = jdbcTemplate.update(
                "UPDATE surgery_order SET consent_obtained = FALSE WHERE id = :id AND consent_obtained = TRUE",
                new MapSqlParameterSource("id", id));
        return updated > 0;
    }

    private String writeJson(List<UUID> nurseIds) {
        try {
            return objectMapper.writeValueAsString(nurseIds == null ? List.of() : nurseIds);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize assigned_nurses", e);
        }
    }

    private List<UUID> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize assigned_nurses", e);
        }
    }

    @Override
    public boolean recordDisposition(UUID id, SurgeryDisposition disposition, UUID recordedBy) {
        int updated = jdbcTemplate.update("""
                        UPDATE surgery_order
                        SET disposition = :disposition, disposition_recorded_by = :recordedBy,
                            disposition_recorded_at = now()
                        WHERE id = :id AND status = 'COMPLETED' AND disposition IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("disposition", disposition.name())
                        .addValue("recordedBy", recordedBy)
                        .addValue("id", id));
        return updated > 0;
    }

    @Override
    public boolean recordCancellation(UUID id, CancellationReason reason, UUID cancelledBy) {
        int updated = jdbcTemplate.update("""
                        UPDATE surgery_order
                        SET status = 'CANCELLED', cancellation_reason = :reason, cancelled_by = :cancelledBy,
                            cancelled_at = now(), version = version + 1
                        WHERE id = :id AND status IN ('SCHEDULED', 'IN_PROGRESS')
                        """,
                new MapSqlParameterSource()
                        .addValue("reason", reason.name())
                        .addValue("cancelledBy", cancelledBy)
                        .addValue("id", id));
        return updated > 0;
    }

    @Override
    public boolean reconcileEmergencyDraft(UUID id, String procedureServiceItemCode, String procedureName, UUID invoiceLineId) {
        int updated = jdbcTemplate.update("""
                        UPDATE surgery_order
                        SET status = 'IN_PROGRESS', procedure_service_item_code = :code, procedure_name = :name,
                            invoice_line_id = :invoiceLineId, version = version + 1
                        WHERE id = :id AND status = 'DRAFT_EMERGENCY'
                        """,
                new MapSqlParameterSource()
                        .addValue("code", procedureServiceItemCode)
                        .addValue("name", procedureName)
                        .addValue("invoiceLineId", invoiceLineId)
                        .addValue("id", id));
        return updated > 0;
    }

    @Override
    public List<SurgeryOrder> findStaleDrafts(Instant olderThan) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE status = 'DRAFT_EMERGENCY' AND scheduled_at < :olderThan "
                        + "AND governance_escalated_at IS NULL",
                new MapSqlParameterSource("olderThan", java.sql.Timestamp.from(olderThan)), this::mapRow);
    }

    @Override
    public boolean markGovernanceEscalated(UUID id) {
        int updated = jdbcTemplate.update(
                "UPDATE surgery_order SET governance_escalated_at = now() "
                        + "WHERE id = :id AND governance_escalated_at IS NULL",
                new MapSqlParameterSource("id", id));
        return updated > 0;
    }

    private SurgeryOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
        String disposition = rs.getString("disposition");
        String cancellationReason = rs.getString("cancellation_reason");
        java.sql.Timestamp governanceEscalatedAt = rs.getTimestamp("governance_escalated_at");
        return new SurgeryOrder(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("encounter_id"),
                rs.getString("procedure_service_item_code"),
                rs.getString("procedure_name"),
                rs.getString("surgery_type"),
                SurgeryUrgency.valueOf(rs.getString("urgency")),
                rs.getString("anesthesia_type"),
                rs.getObject("scheduled_date", java.time.LocalDate.class),
                rs.getString("time_slot"),
                (UUID) rs.getObject("theatre_id"),
                rs.getInt("estimated_duration_minutes"),
                readJson(rs.getString("assigned_nurses")),
                rs.getString("pre_op_notes"),
                rs.getBoolean("consent_obtained"),
                rs.getString("consent_document_key"),
                SurgeryOrderStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("invoice_line_id"),
                (UUID) rs.getObject("scheduled_by"),
                rs.getTimestamp("scheduled_at").toInstant(),
                rs.getInt("version"),
                disposition == null ? null : SurgeryDisposition.valueOf(disposition),
                (UUID) rs.getObject("disposition_recorded_by"),
                rs.getTimestamp("disposition_recorded_at") == null
                        ? null : rs.getTimestamp("disposition_recorded_at").toInstant(),
                cancellationReason == null ? null : CancellationReason.valueOf(cancellationReason),
                (UUID) rs.getObject("cancelled_by"),
                rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant(),
                governanceEscalatedAt == null ? null : governanceEscalatedAt.toInstant());
    }
}
