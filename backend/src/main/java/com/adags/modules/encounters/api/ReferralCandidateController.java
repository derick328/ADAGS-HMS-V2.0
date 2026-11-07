package com.adags.modules.encounters.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.identity.IdentityFacade;
import com.adags.shared.kernel.UserRef;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.Money;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 06-triage.md §5.1: "the fee is visible BEFORE selection... every triage
// assessment now produces a priced charge." Scoped deliberately for this
// pass (user's own call, given no backend existed for any of this):
//   - Provider list + role label: real, from active clinical officer, doctor, and specialist
//     role assignments (IdentityFacade), not filtered by department — no
//     provider-department affiliation is modelled anywhere accessible today.
//   - Fee: real, resolved through BillingFacade.previewConsultationFee —
//     the same role -&gt; service-item -&gt; price path the actual charge posts
//     through at submission.
//   - Queue depth: real — count of this provider's own AWAITING_CONSULT
//     encounters.
//   - On-duty status and an estimated wait: NOT built. No shift-roster
//     wiring connects a work_shift/shift_assignment row to "is this
//     provider on duty right now," and no wait-time model exists — onDuty
//     is always null (shown as "unknown," never guessed) and no wait
//     estimate is returned at all, rather than inventing one.
@RestController
public class ReferralCandidateController {

    private static final List<String> CONSULTATION_ROLE_CODES = List.of("CLINICAL_OFFICER", "DOCTOR", "SPECIALIST_DOCTOR");

    private final EncounterRepository encounterRepository;
    private final IdentityFacade identityFacade;
    private final BillingFacade billingFacade;
    private final AuthorizationService authorizationService;

    public ReferralCandidateController(
            EncounterRepository encounterRepository,
            IdentityFacade identityFacade,
            BillingFacade billingFacade,
            AuthorizationService authorizationService
    ) {
        this.encounterRepository = encounterRepository;
        this.identityFacade = identityFacade;
        this.billingFacade = billingFacade;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/encounters/{encounterId}/referral-candidates")
    public List<ReferralCandidateResponse> forEncounter(@PathVariable UUID encounterId, @AuthenticationPrincipal Actor actor) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!authorizationService.hasPermission(actor, "ENCOUNTER_ROUTE", ScopeRef.of(ScopeType.DEPARTMENT, encounter.departmentId()))) {
            throw new AccessDeniedException("Missing permission: ENCOUNTER_ROUTE");
        }

        // A provider can hold more than one clinical role —
        // dedupe by userId, SPECIALIST_DOCTOR's label wins when both are held,
        // same "highest priority role wins" idea BillingFacadeImpl's fee
        // resolution already applies internally.
        Map<UUID, ReferralCandidateResponse> byProvider = new LinkedHashMap<>();
        for (String roleCode : CONSULTATION_ROLE_CODES) {
            String roleLabel = switch (roleCode) {
                case "SPECIALIST_DOCTOR" -> "Specialist";
                case "CLINICAL_OFFICER" -> "Clinical Officer";
                default -> "Doctor";
            };
            for (UserRef provider : identityFacade.findActiveUsersWithRole(roleCode)) {
                byProvider.put(provider.userId(), buildCandidate(provider, roleLabel, encounter));
            }
        }
        return List.copyOf(byProvider.values());
    }

    private ReferralCandidateResponse buildCandidate(UserRef provider, String roleLabel, Encounter encounter) {
        Long feeAmountMinor = null;
        String currencyCode = null;
        try {
            Money fee = billingFacade.previewConsultationFee(provider.userId(), encounter.payerType());
            feeAmountMinor = fee.amountMinor();
            currencyCode = fee.currencyCode();
        } catch (PriceNotConfiguredException priceUnavailable) {
            // No current price for this provider's role under this
            // encounter's payer category (e.g. INSURANCE — no insurance
            // price list exists yet) — show "fee unavailable," never a
            // fabricated number or a 500 for the whole picker.
        }
        int queueDepth = encounterRepository.countByAssignedProviderAndState(provider.userId(), EncounterState.AWAITING_CONSULT);
        return new ReferralCandidateResponse(
                provider.userId(), provider.username(), roleLabel, feeAmountMinor, currencyCode, queueDepth, null);
    }
}
