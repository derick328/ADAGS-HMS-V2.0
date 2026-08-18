package com.adags.modules.pharmacy.application;

import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.SubstitutionProposal;

// replacementLine is null when the proposal was rejected.
public record SubstitutionReviewResult(SubstitutionProposal proposal, PrescriptionLine cancelledLine, PrescriptionLine replacementLine) {
}
