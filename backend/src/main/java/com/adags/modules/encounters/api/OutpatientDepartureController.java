package com.adags.modules.encounters.api;
import com.adags.modules.encounters.application.OutpatientDepartureService;
import com.adags.shared.authz.Actor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/encounters/{id}/departure")
public class OutpatientDepartureController {
 private final OutpatientDepartureService service;
 public OutpatientDepartureController(OutpatientDepartureService service){this.service=service;}
 public record DepartureRequest(@jakarta.validation.constraints.NotNull UUID facilityId,@jakarta.validation.constraints.NotBlank String outcome,@jakarta.validation.constraints.Size(max=2000) String note) {}
 public record DepartureResponse(UUID departureId) {}
 public record DepartureCorrectionRequest(@jakarta.validation.constraints.NotNull UUID facilityId,@jakarta.validation.constraints.NotNull UUID departureId,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2000) String reason) {}
 @PostMapping public DepartureResponse confirm(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody DepartureRequest request,@AuthenticationPrincipal Actor actor) {
  return new DepartureResponse(service.confirm(id,request.facilityId(),request.outcome(),request.note(),actor));
 }
 @PostMapping("/corrections") public void correct(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody DepartureCorrectionRequest request,@AuthenticationPrincipal Actor actor) {
  service.correct(id,request.facilityId(),request.departureId(),request.reason(),actor);
 }
}
