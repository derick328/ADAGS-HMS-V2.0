package com.adags.modules.insurance.api;

import com.adags.modules.insurance.domain.InsuranceSchemeRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 05-receptionist.md §4.2 ⚠5: the registration form's insurance scheme
// picker — "scheme_id, FK to InsuranceScheme, a managed list, not free
// text." No dedicated permission exists for reading this small managed
// list; gated on PATIENT_CREATE, the action it's actually in service of.
@RestController
@RequestMapping("/api/insurance-schemes")
public class InsuranceSchemeController {

    private final InsuranceSchemeRepository insuranceSchemeRepository;
    private final AuthorizationService authorizationService;

    public InsuranceSchemeController(InsuranceSchemeRepository insuranceSchemeRepository, AuthorizationService authorizationService) {
        this.insuranceSchemeRepository = insuranceSchemeRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<InsuranceSchemeResponse> list(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "PATIENT_CREATE")) {
            throw new AccessDeniedException("Missing permission: PATIENT_CREATE");
        }
        return insuranceSchemeRepository.findAllActive().stream().map(InsuranceSchemeResponse::from).toList();
    }
}
