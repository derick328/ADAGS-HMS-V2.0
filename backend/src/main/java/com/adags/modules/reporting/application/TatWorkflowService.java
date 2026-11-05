package com.adags.modules.reporting.application;

import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Records only deliberate service starts that have no existing source timestamp. */
@Service
public class TatWorkflowService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authorization;

    public TatWorkflowService(NamedParameterJdbcTemplate jdbc, AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.authorization = authorization;
    }

    @Transactional
    public Instant startTriage(UUID encounterId, Actor actor) {
        var rows = jdbc.query("SELECT department_id, state FROM encounter WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", encounterId),
                (rs, n) -> new Target((UUID) rs.getObject("department_id"), rs.getString("state")));
        if (rows.isEmpty()) throw new NoSuchElementException("Encounter not found: " + encounterId);
        Target target = rows.getFirst();
        if (!authorization.hasPermission(actor, "TRIAGE_ASSESS", ScopeRef.of(ScopeType.DEPARTMENT, target.scopeId())))
            throw new AccessDeniedException("Missing permission: TRIAGE_ASSESS");
        var previous = existingStart("TRIAGE", encounterId);
        if (previous != null) return previous;
        if (!target.state().equals("AWAITING_TRIAGE"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Encounter is not awaiting triage.");
        return insertStart(encounterId, "TRIAGE", encounterId, actor.userId());
    }

    @Transactional
    public Instant startPharmacy(UUID prescriptionLineId, Actor actor) {
        var rows = jdbc.query("""
                SELECT po.encounter_id, po.facility_id, pl.status
                FROM prescription_line pl JOIN prescription_order po ON po.id = pl.prescription_order_id
                WHERE pl.id = :id FOR UPDATE OF pl
                """, new MapSqlParameterSource("id", prescriptionLineId),
                (rs, n) -> new PharmacyTarget((UUID) rs.getObject("encounter_id"),
                        (UUID) rs.getObject("facility_id"), rs.getString("status")));
        if (rows.isEmpty()) throw new NoSuchElementException("Prescription line not found: " + prescriptionLineId);
        PharmacyTarget target = rows.getFirst();
        if (!authorization.hasPermission(actor, "DISPENSE_CREATE", ScopeRef.of(ScopeType.FACILITY, target.facilityId())))
            throw new AccessDeniedException("Missing permission: DISPENSE_CREATE");
        var previous = existingStart("PHARMACY", prescriptionLineId);
        if (previous != null) return previous;
        if (!target.status().equals("AWAITING_DISPENSE") && !target.status().equals("PARTIALLY_DISPENSED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prescription line is not awaiting dispensing.");
        return insertStart(target.encounterId(), "PHARMACY", prescriptionLineId, actor.userId());
    }

    private Instant insertStart(UUID encounterId, String workflowType, UUID referenceId, UUID actorId) {
        var params = new MapSqlParameterSource().addValue("encounter", encounterId)
                .addValue("type", workflowType).addValue("reference", referenceId).addValue("actor", actorId);
        jdbc.update("""
                INSERT INTO tat_workflow_event (encounter_id, workflow_type, workflow_reference_id, event_type, recorded_by)
                VALUES (:encounter, :type, :reference, 'STARTED', :actor)
                ON CONFLICT (workflow_type, workflow_reference_id, event_type) DO NOTHING
                """, params);
        return existingStart(workflowType, referenceId);
    }

    private Instant existingStart(String workflowType, UUID referenceId) {
        var rows = jdbc.query("""
                SELECT occurred_at FROM tat_workflow_event
                WHERE workflow_type = :type AND workflow_reference_id = :reference AND event_type = 'STARTED'
                """, new MapSqlParameterSource().addValue("type", workflowType).addValue("reference", referenceId),
                (rs, n) -> rs.getTimestamp(1).toInstant());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private record Target(UUID scopeId, String state) {}
    private record PharmacyTarget(UUID encounterId, UUID facilityId, String status) {}
}
