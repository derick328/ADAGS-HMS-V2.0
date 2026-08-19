package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.AssignShiftCommand;
import com.adags.modules.inpatient.application.AssignShiftService;
import com.adags.modules.inpatient.domain.ShiftAssignment;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 19-work-shifts-roster.md §3: "Rostering is delegated — admin defines the
// shift patterns, department heads or the clinical lead assign people to
// them. ROSTER_MANAGE scoped to a department."
@RestController
@RequestMapping("/api/shift-assignments")
public class ShiftAssignmentController {

    private final AssignShiftService assignShiftService;
    private final AuthorizationService authorizationService;

    public ShiftAssignmentController(AssignShiftService assignShiftService, AuthorizationService authorizationService) {
        this.assignShiftService = assignShiftService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftAssignmentResponse assign(@Valid @RequestBody AssignShiftRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(
                actor, "ROSTER_MANAGE", ScopeRef.of(ScopeType.DEPARTMENT, request.departmentId()))) {
            throw new AccessDeniedException("Missing permission: ROSTER_MANAGE");
        }
        ShiftAssignment assignment = assignShiftService.assign(new AssignShiftCommand(
                request.userId(), request.workShiftId(), request.shiftDate(), request.wardId(),
                request.departmentId(), request.roleContext(), actor.userId()));
        return ShiftAssignmentResponse.from(assignment);
    }
}
