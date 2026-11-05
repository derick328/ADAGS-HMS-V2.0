package com.adags.modules.reporting.api;
import com.adags.modules.reporting.application.PatientPresenceService;
import com.adags.shared.authz.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/patient-presence")
public class PatientPresenceController {
 private final PatientPresenceService service; private final AuthorizationService auth;
 public PatientPresenceController(PatientPresenceService service,AuthorizationService auth) {this.service=service;this.auth=auth;}
 private void require(Actor actor,UUID facility) {
  if(!auth.hasAnyPermission(actor,ScopeRef.of(ScopeType.FACILITY,facility),"ENCOUNTER_OPEN","TAT_ANALYTICS_READ","PATIENT_READ_VISIT_HISTORY")) throw new AccessDeniedException("Missing facility visit access");
 }
 @GetMapping public List<PatientPresenceService.Presence> list(@RequestParam UUID facilityId,@RequestParam(required=false) UUID patientId,@RequestParam(defaultValue="false") boolean departed,@AuthenticationPrincipal Actor actor) {
  require(actor,facilityId);return service.list(facilityId,patientId,departed);
 }
 @GetMapping("/summaries") public List<PatientPresenceService.VisitSummary> summaries(@RequestParam UUID facilityId,@RequestParam List<UUID> patientIds,@AuthenticationPrincipal Actor actor) {
  require(actor,facilityId);return service.summaries(facilityId,patientIds);
 }
}
