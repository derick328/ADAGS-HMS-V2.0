package com.adags.modules.insurance.application;

import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.InsuranceClaim;
import com.adags.modules.insurance.domain.InsuranceClaimRepository;
import com.adags.modules.insurance.domain.InvalidClaimTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 7 item 3 part 3: "DRAFT -> SUBMITTED -> APPROVED | REJECTED;
// REJECTED -> RESUBMITTED -> APPROVED | REJECTED." Transitions only — no
// actual submission transport exists (no scheme API); this is what a
// future admin/accountant-facing action (or, once built, an actual
// scheme integration) would call.
@Service
public class TransitionInsuranceClaimService {

    private final InsuranceClaimRepository insuranceClaimRepository;

    public TransitionInsuranceClaimService(InsuranceClaimRepository insuranceClaimRepository) {
        this.insuranceClaimRepository = insuranceClaimRepository;
    }

    @Transactional
    public InsuranceClaim transition(TransitionInsuranceClaimCommand command) {
        InsuranceClaim claim = insuranceClaimRepository.findById(command.claimId())
                .orElseThrow(() -> new NoSuchElementException("Insurance claim not found: " + command.claimId()));
        if (!claim.state().canTransitionTo(command.targetState())) {
            throw new InvalidClaimTransitionException(claim.state(), command.targetState());
        }
        if (command.targetState() == ClaimState.REJECTED
                && (command.rejectedReason() == null || command.rejectedReason().isBlank())) {
            throw new IllegalArgumentException("A rejected reason is required.");
        }
        return insuranceClaimRepository.transition(claim.id(), claim.state(), command.targetState(), command.rejectedReason());
    }
}
