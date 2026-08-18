package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultParameterValueRepository;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.ResultNotPendingVerificationException;
import com.adags.modules.laboratory.domain.ResultVersionHash;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 16-lab-verification-model.md v0.2 §8: "the mechanism is permission-based
// ... no migration, no code change, no backfill" to activate it later —
// this service is that mechanism, dormant today because LAB_RESULT_VERIFY
// is granted to nobody (V5's catalogue), not because it doesn't exist.
// The entered_by != verified_by rule is the DB CHECK
// (verification_actor_valid, V22) — this service doesn't duplicate it,
// since the constraint is what actually stops a stale-read race.
@Service
public class VerifyLabResultService {

    private final LabResultRepository labResultRepository;
    private final LabResultParameterValueRepository labResultParameterValueRepository;

    public VerifyLabResultService(
            LabResultRepository labResultRepository,
            LabResultParameterValueRepository labResultParameterValueRepository
    ) {
        this.labResultRepository = labResultRepository;
        this.labResultParameterValueRepository = labResultParameterValueRepository;
    }

    @Transactional
    public void verify(VerifyLabResultCommand command) {
        LabResult result = labResultRepository.findById(command.resultId())
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + command.resultId()));

        String hash = ResultVersionHash.compute(labResultParameterValueRepository.findByResultId(result.id()));
        boolean updated = labResultRepository.verify(result.id(), command.verifiedBy(), command.verificationComment(), hash);
        if (!updated) {
            throw new ResultNotPendingVerificationException(result.id());
        }
    }
}
