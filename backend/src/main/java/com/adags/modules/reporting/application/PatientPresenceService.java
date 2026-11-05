package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Administrative projection only: no clinical narratives, diagnoses or orders. */
@Service
public class PatientPresenceService {
 private final NamedParameterJdbcTemplate jdbc;
 public PatientPresenceService(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
 public record Presence(UUID encounterId, UUID patientId, String patientName, String hospitalNo,
   String state, String type, String stage, String presence, Instant arrivedAt, Instant departedAt, long visitNumber, UUID departureId) {}
 public List<Presence> list(UUID facilityId, UUID patientId, boolean departed) {
  var params=new HashMap<String,Object>(); params.put("facility",facilityId);
  String filter="";
  if(patientId!=null) { params.put("patient",patientId); filter=" AND patient_id=(SELECT patient_id FROM patient_canonical_identity WHERE alias_id=:patient)"; }
  else filter=departed ? " AND departed_at IS NOT NULL" : " AND (departed_at IS NULL OR presence='Needs review')";
  return jdbc.query("SELECT * FROM patient_facility_presence WHERE facility_id=:facility"+filter+" ORDER BY arrived_at DESC LIMIT 200",params,(rs,n)->new Presence(
   rs.getObject("encounter_id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getString("patient_name"),rs.getString("hospital_no"),
   rs.getString("state"),rs.getString("type"),rs.getString("stage"),rs.getString("presence"),rs.getTimestamp("arrived_at").toInstant(),
   rs.getTimestamp("departed_at")==null?null:rs.getTimestamp("departed_at").toInstant(),rs.getLong("visit_number"),rs.getObject("departure_id",UUID.class)));
 }
 public record VisitSummary(UUID patientId,long totalVisits,long completedVisits,Instant firstVisit,Instant latestVisit,boolean activeEncounter) {}
 public List<VisitSummary> summaries(UUID facilityId,List<UUID> patientIds) {
  if(patientIds.isEmpty() || patientIds.size()>100) throw new IllegalArgumentException("Select 1–100 patients.");
  return jdbc.query("""
   SELECT requested.alias_id, count(v.encounter_id) AS total,
   count(*) FILTER (WHERE v.state IN ('CLOSED','DISCHARGED')) AS completed,
   min(v.arrived_at) AS first_visit,max(v.arrived_at) AS latest_visit,
   coalesce(bool_or(v.state NOT IN ('CLOSED','DISCHARGED','REFERRED_OUT','DECEASED','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID')),false) AS active
   FROM patient_canonical_identity requested LEFT JOIN patient_facility_presence v
   ON v.patient_id=requested.patient_id AND v.facility_id=:facility
   WHERE requested.alias_id IN (:patients) GROUP BY requested.alias_id
   """,Map.of("facility",facilityId,"patients",patientIds),(rs,n)->new VisitSummary(rs.getObject("alias_id",UUID.class),rs.getLong("total"),rs.getLong("completed"),
   rs.getTimestamp("first_visit")==null?null:rs.getTimestamp("first_visit").toInstant(),rs.getTimestamp("latest_visit")==null?null:rs.getTimestamp("latest_visit").toInstant(),rs.getBoolean("active")));
 }
}
