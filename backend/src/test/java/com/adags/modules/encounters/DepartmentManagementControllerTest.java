package com.adags.modules.encounters;

import com.adags.modules.encounters.api.DepartmentManagementController;
import com.adags.modules.encounters.application.DepartmentManagementService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DepartmentManagementControllerTest {
    @Test void unauthorizedUserCannotCreateOrAppoint() {
        var service=mock(DepartmentManagementService.class);
        var authorization=mock(AuthorizationService.class);
        var controller=new DepartmentManagementController(service,authorization);
        UUID facility=UUID.randomUUID(), unit=UUID.randomUUID();
        when(service.facilityId(unit)).thenReturn(facility);
        Actor actor=new Actor(UUID.randomUUID());
        assertThatThrownBy(() -> controller.create(new DepartmentManagementService.UnitCreate(
            facility,UUID.randomUUID(),"TEST","Test",null,"CONSULTATION",true),actor))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.appoint(unit,new DepartmentManagementController.HodRequest(UUID.randomUUID()),actor))
            .isInstanceOf(AccessDeniedException.class);
        verify(service,never()).appoint(any(),any(),any());
    }
}
