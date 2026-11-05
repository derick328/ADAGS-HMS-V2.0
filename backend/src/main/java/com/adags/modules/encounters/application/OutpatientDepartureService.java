package com.adags.modules.encounters.application;
import com.adags.shared.authz.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class OutpatientDepartureService {
 private final NamedParameterJdbcTemplate jdbc; private final AuthorizationService auth; private final AuditRecorder audit;
 public OutpatientDepartureService(NamedParameterJdbcTemplate jdbc,AuthorizationService auth,AuditRecorder audit) {this.jdbc=jdbc;this.auth=auth;this.audit=audit;}
 private Map<String,Object> encounter(UUID id,UUID facility,Actor actor,boolean correction) {
  var rows=jdbc.queryForList("SELECT * FROM encounter WHERE id=:id AND facility_id=:facility FOR UPDATE",Map.of("id",id,"facility",facility));
  if(rows.isEmpty()) throw new NoSuchElementException("Encounter not found in facility");
  var row=rows.getFirst(); var scope=ScopeRef.of(ScopeType.DEPARTMENT,(UUID)row.get("department_id"));
  if(!(correction ? auth.hasPermission(actor,"ENCOUNTER_CLOSE_ADMIN",scope) : auth.hasAnyPermission(actor,scope,"ENCOUNTER_OPEN","ENCOUNTER_CLOSE_ADMIN"))) throw new AccessDeniedException("Missing departure management permission");
  return row;
 }
 @Transactional public UUID confirm(UUID id,UUID facility,String outcome,String note,Actor actor) {
  var e=encounter(id,facility,actor,false);
  var p=new HashMap<String,Object>();p.put("id",id);p.put("facility",facility);p.put("actor",actor.userId());p.put("outcome",outcome);p.put("note",note);
  var existing=jdbc.queryForList("SELECT d.id FROM current_outpatient_departure d WHERE encounter_id=:id",p,UUID.class);
  if(!existing.isEmpty()) return existing.getFirst();
  jdbc.queryForList("SELECT id FROM surgery_order WHERE encounter_id=:id FOR UPDATE",p);
  if(Boolean.TRUE.equals(jdbc.queryForObject("""
   SELECT EXISTS(SELECT 1 FROM admission WHERE encounter_id=:id)
   OR EXISTS(SELECT 1 FROM surgery_order WHERE encounter_id=:id AND
    (status IN ('IN_PROGRESS','DRAFT_EMERGENCY') OR (status='COMPLETED' AND
     (disposition IS NULL OR disposition IN ('RECOVERY_THEN_DISCHARGE','ADMIT_TO_WARD','DECEASED')))))
   """,p,Boolean.class))) throw new IllegalArgumentException("Use the inpatient or surgical discharge workflow; recovery must be reconciled clinically.");
  String state=(String)e.get("state");
  boolean valid=com.adags.modules.encounters.domain.OutpatientDeparturePolicy.permits(state,outcome);
  if(!valid) throw new IllegalArgumentException("Reconcile the clinical departure outcome before confirming departure.");
  if(note!=null && note.length()>2000) throw new IllegalArgumentException("Departure note exceeds 2000 characters.");
  UUID departure=jdbc.queryForObject("INSERT INTO outpatient_departure(encounter_id,facility_id,recorded_by,outcome,note) VALUES (:id,:facility,:actor,:outcome,:note) RETURNING id",p,UUID.class);
  audit.record(actor.userId(),"OUTPATIENT_DEPARTURE_CONFIRMED","encounter",id,(UUID)e.get("patient_id"));return departure;
 }
 @Transactional public void correct(UUID id,UUID facility,UUID departure,String reason,Actor actor) {
  var e=encounter(id,facility,actor,true);
  if(reason==null || reason.isBlank() || reason.length()>2000) throw new IllegalArgumentException("Enter a correction reason (up to 2000 characters).");
  int changed=jdbc.update("""
   INSERT INTO outpatient_departure_correction(departure_id,recorded_by,reason)
   SELECT id,:actor,:reason FROM outpatient_departure WHERE id=:departure AND encounter_id=:id AND facility_id=:facility
   ON CONFLICT DO NOTHING
   """,Map.of("actor",actor.userId(),"reason",reason,"departure",departure,"id",id,"facility",facility));
  if(changed>0) audit.record(actor.userId(),"OUTPATIENT_DEPARTURE_CORRECTED","encounter",id,(UUID)e.get("patient_id"));
 }
}
