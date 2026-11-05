package com.adags.modules.encounters.infrastructure;
import com.adags.shared.authz.CareRelationshipPort;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
/** Active treatment assignment establishes continuity access; scope is checked separately. */
@Component
@Primary
public class EncounterCareRelationship implements CareRelationshipPort {
 private final NamedParameterJdbcTemplate jdbc;
 public EncounterCareRelationship(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public boolean hasCareRelationship(UUID actor,UUID patient) {
  return Boolean.TRUE.equals(jdbc.queryForObject("""
   SELECT EXISTS(SELECT 1 FROM encounter e
    JOIN patient_canonical_identity ci ON ci.alias_id=e.patient_id
    WHERE ci.patient_id=(SELECT patient_id FROM patient_canonical_identity WHERE alias_id=:patient)
    AND e.assigned_provider_id=:actor
    AND e.state NOT IN ('CANCELLED_IN_ERROR','CLOSED','DISCHARGED','REFERRED_OUT','DECEASED','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID'))
   """,Map.of("actor",actor,"patient",patient),Boolean.class));
 }
}
