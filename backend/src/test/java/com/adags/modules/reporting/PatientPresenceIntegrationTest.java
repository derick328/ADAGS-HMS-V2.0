package com.adags.modules.reporting;
import com.adags.modules.reporting.application.PatientPresenceService;
import com.adags.modules.reporting.application.TatAnalyticsService;
import com.adags.modules.encounters.application.OutpatientDepartureService;
import com.adags.shared.authz.Actor;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@Transactional
class PatientPresenceIntegrationTest extends IntegrationTestSupport {
 @Autowired NamedParameterJdbcTemplate jdbc;
 @Autowired PatientPresenceService presence;
 @Autowired OutpatientDepartureService departure;
 @Autowired TatAnalyticsService tat;
 private static final UUID FACILITY=UUID.fromString("00000000-0000-0000-0000-000000000001");
 private UUID receptionistId;
 @Test void physicalDepartureIsIdempotentAndCorrectionPreservesHistory() {
  UUID id=fixture("CLOSED");
  assertThat(tat.encounterJourney(FACILITY,id).visit().endedAt()).isNull();
  UUID recorded=departure.confirm(id,FACILITY,"CARE_COMPLETE",null,actor());
  assertThat(departure.confirm(id,FACILITY,"CARE_COMPLETE",null,actor())).isEqualTo(recorded);
  assertThat(tat.encounterJourney(FACILITY,id).visit().endedAt()).isNotNull();
  departure.correct(id,FACILITY,recorded,"Wrong encounter selected",actor());
  assertThat(tat.encounterJourney(FACILITY,id).visit().endedAt()).isNull();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_departure WHERE id=:id",Map.of("id",recorded),Integer.class)).isEqualTo(1);
 }
 @Test void followUpDepartureDoesNotFinalizeEncounter() {
  UUID id=fixture("AWAITING_RESULTS"); departure.confirm(id,FACILITY,"FOLLOW_UP_PENDING",null,actor());
  var row=presence.list(FACILITY,null,true).stream().filter(v->v.encounterId().equals(id)).findFirst().orElseThrow();
  assertThat(row.presence()).isEqualTo("Away — follow-up pending");assertThat(row.state()).isEqualTo("AWAITING_RESULTS");
 }
 @Test void rejectsWrongFacilityAndFalseClinicalCompletion() {
  UUID id=fixture("IN_CONSULT");
  assertThatThrownBy(()->departure.confirm(id,FACILITY,"CARE_COMPLETE",null,actor())).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->departure.confirm(id,UUID.randomUUID(),"CARE_COMPLETE",null,actor())).isInstanceOf(NoSuchElementException.class);
 }
 @Test void adminWithoutEncounterPermissionCannotConfirmDeparture() {
  UUID id=fixture("CLOSED");
  UUID adminId=jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'",Map.of(),UUID.class);
  assertThatThrownBy(()->departure.confirm(id,FACILITY,"CARE_COMPLETE",null,new Actor(adminId)))
      .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 private Actor actor(){return new Actor(receptionistId);}
 private UUID fixture(String state) {
  receptionistId=jdbc.queryForObject("""
      INSERT INTO app_user(username,password_hash,status)
      SELECT :username,password_hash,'ACTIVE' FROM app_user WHERE username='admin' RETURNING id
      """,Map.of("username","presence-"+UUID.randomUUID()),UUID.class);
  jdbc.update("""
      INSERT INTO role_assignment(user_id,role_id,scope_type,scope_id)
      SELECT :actor,id,'GLOBAL',NULL FROM role WHERE code='RECEPTIONIST'
      """,Map.of("actor",receptionistId));
  UUID p=jdbc.queryForObject("INSERT INTO patient(hospital_no,given_name,family_name,date_of_birth,gender,phone) VALUES(:number,'Presence','Test',DATE '1990-01-01','MALE','+255700000000') RETURNING id",Map.of("number","P-"+UUID.randomUUID()),UUID.class);
  UUID d=jdbc.queryForObject("INSERT INTO department(facility_id,code,name) VALUES(:facility,:code,'Presence') RETURNING id",Map.of("facility",FACILITY,"code","P-"+UUID.randomUUID()),UUID.class);
  return jdbc.queryForObject("INSERT INTO encounter(patient_id,facility_id,department_id,type,state,payer_type,created_by) VALUES(:patient,:facility,:department,'OUTPATIENT',:state,'CASH',:actor) RETURNING id",Map.of("patient",p,"facility",FACILITY,"department",d,"state",state,"actor",actor().userId()),UUID.class);
 }
}
