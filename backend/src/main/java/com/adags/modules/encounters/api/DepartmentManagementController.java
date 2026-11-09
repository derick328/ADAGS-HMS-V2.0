package com.adags.modules.encounters.api;

import com.adags.modules.encounters.application.DepartmentManagementService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/department-management")
public class DepartmentManagementController {
    private final DepartmentManagementService service;
    private final AuthorizationService auth;
    public DepartmentManagementController(DepartmentManagementService service, AuthorizationService auth) {
        this.service=service; this.auth=auth;
    }
    private void require(Actor actor) {
        if(!auth.hasPermission(actor,"USER_MANAGE")) throw new AccessDeniedException("Missing permission: USER_MANAGE");
    }
    private void requireUnit(Actor actor, UUID unitId) {
        if (!auth.hasPermission(actor,"USER_MANAGE",ScopeRef.of(ScopeType.FACILITY,service.facilityId(unitId))))
            throw new AccessDeniedException("Missing facility department management permission.");
    }
    @GetMapping("/categories")
    public List<DepartmentManagementService.Category> categories(@AuthenticationPrincipal Actor actor) {
        require(actor); return service.categories();
    }
    @GetMapping("/units")
    public List<DepartmentManagementService.Unit> units(@AuthenticationPrincipal Actor actor) {
        require(actor); return service.units(true).stream().filter(u ->
            auth.hasPermission(actor,"USER_MANAGE",ScopeRef.of(ScopeType.FACILITY,service.facilityId(u.id())))).toList();
    }
    @GetMapping("/units/{id}/staff")
    public List<DepartmentManagementService.Staff> staff(@PathVariable UUID id,@AuthenticationPrincipal Actor actor) {
        requireUnit(actor,id); return service.staff(id);
    }
    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID create(@RequestBody DepartmentManagementService.UnitCreate value,@AuthenticationPrincipal Actor actor) {
        if (value.facilityId()==null || !auth.hasPermission(actor,"USER_MANAGE",ScopeRef.of(ScopeType.FACILITY,value.facilityId())))
            throw new AccessDeniedException("Missing facility department management permission.");
        return service.create(value,actor.userId());
    }
    @PutMapping("/units/{id}")
    public void update(@PathVariable UUID id,@RequestBody DepartmentManagementService.UnitUpdate value,@AuthenticationPrincipal Actor actor) {
        requireUnit(actor,id); service.update(id,value,actor.userId());
    }
    public record HodRequest(UUID userId) {}
    @PutMapping("/units/{id}/hod")
    public void appoint(@PathVariable UUID id,@RequestBody HodRequest value,@AuthenticationPrincipal Actor actor) {
        requireUnit(actor,id); if(value.userId()==null) throw new IllegalArgumentException("Choose a staff member."); service.appoint(id,value.userId(),actor.userId());
    }
    @DeleteMapping("/units/{id}/hod")
    public void vacate(@PathVariable UUID id,@AuthenticationPrincipal Actor actor) {
        requireUnit(actor,id); service.vacate(id,actor.userId());
    }
}
