package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.AssignShiftCommand;
import com.adags.modules.inpatient.application.AssignShiftService;
import com.adags.modules.inpatient.domain.ShiftAssignment;
import com.adags.modules.inpatient.domain.ShiftAssignmentRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 19-work-shifts-roster.md §3: "Rostering is delegated — admin defines the
// shift patterns, department heads or the clinical lead assign people to
// them. ROSTER_MANAGE scoped to a department."
@RestController
@RequestMapping("/api/shift-assignments")
public class ShiftAssignmentController {

    private final AssignShiftService assignShiftService;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AuthorizationService authorizationService;

    public ShiftAssignmentController(
            AssignShiftService assignShiftService, ShiftAssignmentRepository shiftAssignmentRepository,
            AuthorizationService authorizationService
    ) {
        this.assignShiftService = assignShiftService;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
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

    // 17-ward-nurse.md §7: "Incoming nurse (from roster)" — the picker
    // Shift Handover reads to identify who to hand over to, same roster
    // ShiftAssignmentRepository's own javadoc describes.
    @GetMapping
    public List<ShiftAssignmentResponse> listByWardAndDate(
            @RequestParam UUID wardId, @RequestParam LocalDate shiftDate, @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "SHIFT_HANDOVER_CREATE", ScopeRef.of(ScopeType.WARD, wardId))) {
            throw new AccessDeniedException("Missing permission: SHIFT_HANDOVER_CREATE");
        }
        return shiftAssignmentRepository.findByWardIdAndShiftDate(wardId, shiftDate).stream()
                .map(ShiftAssignmentResponse::from).toList();
    }
}
