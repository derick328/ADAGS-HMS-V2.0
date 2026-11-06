package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.api.LabResultDraftController.DraftResponse;
import com.adags.modules.laboratory.api.LabResultDraftController.SaveDraftRequest;
import com.adags.modules.laboratory.api.LabResultDraftController.SubmitDraftRequest;
import com.adags.modules.laboratory.api.LabResultResponse;
import com.adags.modules.laboratory.api.RecordLabResultRequest;
import com.adags.modules.laboratory.domain.*;
import com.adags.shared.authz.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LabResultDraftService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LabOrderRepository orders;
    private final SpecimenRepository specimens;
    private final AuthorizationService authorization;
    private final RecordLabResultService release;
    private final MachineOutputRegistry machineOutputRegistry;
    private final com.adags.shared.events.DomainEventPublisher events;

    public LabResultDraftService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, LabOrderRepository orders,
                                    SpecimenRepository specimens, AuthorizationService authorization,
                                    RecordLabResultService release, MachineOutputRegistry machineOutputRegistry,
                                    com.adags.shared.events.DomainEventPublisher events) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.orders = orders;
        this.specimens = specimens;
        this.authorization = authorization;
        this.release = release;
        this.machineOutputRegistry = machineOutputRegistry;
        this.events = events;
    }

    public DraftResponse get(UUID labOrderId, Actor actor) {
        LabOrder order = requireOrder(labOrderId, actor);
        return find(order.id()).map(d -> visible(d, actor)).orElse(null);
    }

    @Transactional
    public DraftResponse save(UUID labOrderId, SaveDraftRequest request,
                              Actor actor) {
        LabOrder order = requireOrder(labOrderId, actor);
        Specimen specimen = requireSpecimen(order);
        if (request.payload() != null && request.payload().toString().length() > 262_144)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Draft exceeds the 256 KB limit.");
        if (request.payload() == null || !request.payload().isObject())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft payload must be an object.");
        DraftResponse existing = find(order.id()).orElse(null);
        if (existing != null) {
            visible(existing, actor);
            if (!existing.specimenId().equals(specimen.id()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Specimen changed after the draft was started.");
            if (existing.status().equals("SUBMITTED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Result already submitted.");
            if (request.version() == null || !request.version().equals(existing.version()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft changed in another session. Reload before saving.");
            int changed = jdbc.update("""
                    UPDATE lab_result_draft SET payload = CAST(:payload AS jsonb), version = version + 1, updated_at = now()
                    WHERE id = :id AND version = :version AND status = 'DRAFT'
                    """, new MapSqlParameterSource().addValue("payload", request.payload().toString())
                    .addValue("id", existing.id()).addValue("version", existing.version()));
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft changed in another session.");
        } else {
            if (request.version() != null && request.version() != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft version is stale.");
            try {
                jdbc.update("""
                    INSERT INTO lab_result_draft (lab_order_id, specimen_id, facility_id, owner_id, payload)
                    VALUES (:orderId, :specimenId, :facilityId, :ownerId, CAST(:payload AS jsonb))
                    """, new MapSqlParameterSource().addValue("orderId", order.id()).addValue("specimenId", specimen.id())
                    .addValue("facilityId", order.facilityId()).addValue("ownerId", actor.userId())
                    .addValue("payload", request.payload().toString()));
            } catch (org.springframework.dao.DuplicateKeyException conflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft was created in another session. Reload before saving.");
            }
        }
        DraftResponse saved = find(order.id()).orElseThrow();
        events.publish("LabResultDraftSaved", java.util.Map.of(
                "draftId", saved.id().toString(), "labOrderId", order.id().toString(),
                "specimenId", specimen.id().toString(), "ownerId", actor.userId().toString()));
        return saved;
    }

    @Transactional
    public LabResultResponse submit(UUID labOrderId, SubmitDraftRequest request,
                                    Actor actor) {
        LabOrder order = requireOrder(labOrderId, actor);
        DraftResponse draft = jdbc.query("""
                SELECT id, lab_order_id, specimen_id, facility_id, owner_id, payload::text AS payload,
                       version, status, submitted_result_id, created_at, updated_at
                FROM lab_result_draft WHERE lab_order_id = :id FOR UPDATE
                """, new MapSqlParameterSource("id", order.id()), this::map).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Save a draft before submission."));
        visible(draft, actor);
        if (draft.status().equals("SUBMITTED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Result already submitted.");
        if (request.version() == null || !request.version().equals(draft.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft changed in another session.");
        RecordLabResultRequest result = request.result();
        if (result == null || !draft.specimenId().equals(result.specimenId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Draft and specimen do not match.");
        applySpecimenCorrection(draft);
        var values = result.parameterValues() == null ? List.<ParameterEntry>of() : result.parameterValues().stream()
                .map(p -> new ParameterEntry(p.parameterId(), p.resultValue(), p.overrideFlag(), p.overrideReason())).toList();
        var reagents = result.reagents() == null ? List.<ReagentEntry>of() : result.reagents().stream()
                .map(r -> new ReagentEntry(r.stockBatchId(), r.quantityUsed(), r.note())).toList();
        machineOutputRegistry.requireOwned(result.machineOutputObjectKey(), order.id(), actor.userId());
        var released = release.record(new RecordLabResultCommand(result.specimenId(), actor.userId(),
                result.methodology(), result.reagentsUsedNote(), result.findings(), result.interpretation(),
                result.referenceRangeNote(), result.conclusion(), result.machineOutputObjectKey(), values, reagents,
                result.resultType(), result.singleValue(), result.singleValueUnit(), result.overallInterpretation(),
                result.remarks(), result.labReportObjectKey()));
        jdbc.update("""
                UPDATE lab_result_draft SET status = 'SUBMITTED', submitted_result_id = :resultId,
                    version = version + 1, updated_at = now() WHERE id = :id
                """, new MapSqlParameterSource("resultId", released.result().id()).addValue("id", draft.id()));
        events.publish("LabResultDraftSubmitted", java.util.Map.of(
                "draftId", draft.id().toString(), "resultId", released.result().id().toString(),
                "labOrderId", order.id().toString()));
        return LabResultResponse.from(released);
    }

    private void applySpecimenCorrection(DraftResponse draft) {
        var payload = draft.payload();
        if (!payload.hasNonNull("sampleType") || !payload.hasNonNull("specimenQuality")) return;
        Specimen specimen = specimens.findById(draft.specimenId())
                .orElseThrow(() -> new NoSuchElementException("Specimen not found: " + draft.specimenId()));
        String sampleType = payload.path("sampleType").asText().trim();
        String quality = payload.path("specimenQuality").asText();
        String qualityNote = payload.path("specimenQualityNote").asText().trim();
        String correctionReason = payload.path("sampleTypeCorrectionReason").asText().trim();
        if (sampleType.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the sample type.");
        if (!quality.equals("ADEQUATE"))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A sample with a quality issue cannot be released. Arrange recollection before entering a result.");
        boolean typeChanged = !sampleType.equals(specimen.sampleType());
        if (typeChanged && correctionReason.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explain the sample type correction.");
        if (!typeChanged && qualityNote.equals(specimen.qualityNote() == null ? "" : specimen.qualityNote())) return;
        int corrected = jdbc.update("""
                UPDATE specimen SET sample_type = :sampleType, quality = 'ADEQUATE', quality_note = :qualityNote,
                    sample_type_override_reason = :correctionReason
                WHERE id = :id AND status = 'IN_PROGRESS'
                """, new MapSqlParameterSource("sampleType", sampleType)
                .addValue("qualityNote", qualityNote.isEmpty() ? null : qualityNote)
                .addValue("correctionReason", typeChanged ? correctionReason : specimen.sampleTypeOverrideReason())
                .addValue("id", specimen.id()));
        if (corrected != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Specimen is no longer available for result entry.");
        events.publish("SpecimenCorrectedAtResultEntry", java.util.Map.of(
                "specimenId", specimen.id().toString(), "labOrderId", specimen.labOrderId().toString(),
                "sampleTypeChanged", typeChanged));
    }

    private LabOrder requireOrder(UUID id, Actor actor) {
        LabOrder order = orders.findById(id).orElseThrow(() -> new NoSuchElementException("Lab order not found: " + id));
        if (!authorization.hasPermission(actor, "LAB_RESULT_ENTER", ScopeRef.of(ScopeType.FACILITY, order.facilityId())))
            throw new AccessDeniedException("Missing permission: LAB_RESULT_ENTER");
        return order;
    }

    private Specimen requireSpecimen(LabOrder order) {
        return specimens.findByLabOrderId(order.id()).stream().filter(s -> s.status() == SpecimenStatus.IN_PROGRESS)
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No processable specimen."));
    }

    private DraftResponse visible(DraftResponse draft, Actor actor) {
        if (!draft.ownerId().equals(actor.userId())) throw new AccessDeniedException("Draft belongs to another technician.");
        return draft;
    }

    private java.util.Optional<DraftResponse> find(UUID orderId) {
        return jdbc.query("""
                SELECT id, lab_order_id, specimen_id, facility_id, owner_id, payload::text AS payload,
                       version, status, submitted_result_id, created_at, updated_at
                FROM lab_result_draft WHERE lab_order_id = :id
                """, new MapSqlParameterSource("id", orderId), this::map).stream().findFirst();
    }

    private DraftResponse map(ResultSet rs, int row) throws SQLException {
        try {
            return new DraftResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("specimen_id"),
                    (UUID) rs.getObject("owner_id"), mapper.readTree(rs.getString("payload")), rs.getLong("version"),
                    rs.getString("status"), (UUID) rs.getObject("submitted_result_id"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        } catch (java.io.IOException ex) { throw new SQLException("Invalid saved draft", ex); }
    }

}
