package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.application.DefineWorkShiftCommand;
import com.adags.modules.inpatient.application.DefineWorkShiftService;
import com.adags.modules.inpatient.domain.WorkShift;
import com.adags.modules.inpatient.domain.WorkShiftRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 19-work-shifts-roster.md §2/§5: "Admin defines the patterns once; they
// recur." Unscoped permission check (no ScopeRef) — a shift pattern is a
// definition, not a place, same treatment ItemController gives the
// catalogue (root CLAUDE.md "Facility": definitions don't carry facility_id).
@RestController
@RequestMapping("/api/work-shifts")
public class WorkShiftController {

    private final DefineWorkShiftService defineWorkShiftService;
    private final WorkShiftRepository workShiftRepository;
    private final AuthorizationService authorizationService;

    public WorkShiftController(
            DefineWorkShiftService defineWorkShiftService,
            WorkShiftRepository workShiftRepository,
            AuthorizationService authorizationService
    ) {
        this.defineWorkShiftService = defineWorkShiftService;
        this.workShiftRepository = workShiftRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkShiftResponse define(@Valid @RequestBody DefineWorkShiftRequest request, @AuthenticationPrincipal Actor actor) {
        requireWorkShiftManage(actor);
        WorkShift workShift = defineWorkShiftService.define(new DefineWorkShiftCommand(
                request.code(), request.name(), request.departmentId(), request.startTime(), request.endTime(),
                request.crossesMidnight(), request.daysOfWeek(), actor.userId()));
        return WorkShiftResponse.from(workShift);
    }

    @GetMapping
    public List<WorkShiftResponse> listActive(@AuthenticationPrincipal Actor actor) {
        requireWorkShiftManage(actor);
        return workShiftRepository.findActive().stream().map(WorkShiftResponse::from).toList();
    }

    private void requireWorkShiftManage(Actor actor) {
        if (!authorizationService.hasPermission(actor, "WORK_SHIFT_MANAGE")) {
            throw new AccessDeniedException("Missing permission: WORK_SHIFT_MANAGE");
        }
    }
}
