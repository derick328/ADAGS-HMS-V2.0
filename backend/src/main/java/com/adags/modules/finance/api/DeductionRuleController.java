package com.adags.modules.finance.api;

import com.adags.modules.finance.application.DefineDeductionRuleCommand;
import com.adags.modules.finance.application.DefineDeductionRuleService;
import com.adags.modules.finance.domain.DeductionRuleRepository;
import com.adags.modules.finance.domain.EmploymentType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 18-admin.md §9: "PAYROLL_MANAGE as its own permission even though
// ADMIN holds it today" — gated on that code, not the ADMIN role name.
@RestController
@RequestMapping("/api/finance/deduction-rules")
public class DeductionRuleController {

    private final DefineDeductionRuleService defineDeductionRuleService;
    private final DeductionRuleRepository deductionRuleRepository;
    private final AuthorizationService authorizationService;

    public DeductionRuleController(
            DefineDeductionRuleService defineDeductionRuleService,
            DeductionRuleRepository deductionRuleRepository,
            AuthorizationService authorizationService
    ) {
        this.defineDeductionRuleService = defineDeductionRuleService;
        this.deductionRuleRepository = deductionRuleRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeductionRuleResponse define(@Valid @RequestBody DefineDeductionRuleRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PAYROLL_MANAGE");
        var rule = defineDeductionRuleService.define(new DefineDeductionRuleCommand(request.employmentType(),
                request.deductionCode(), request.calculationMethod(), request.rateOrAmount(),
                request.thresholdMinor(), request.isEmployerPaid(), actor.userId()));
        return DeductionRuleResponse.from(rule);
    }

    @GetMapping("/effective")
    public List<DeductionRuleResponse> findEffective(
            @RequestParam EmploymentType employmentType,
            @RequestParam(required = false) LocalDate asOf,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PAYROLL_MANAGE");
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        return deductionRuleRepository.findEffective(employmentType, date).stream()
                .map(DeductionRuleResponse::from)
                .toList();
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
