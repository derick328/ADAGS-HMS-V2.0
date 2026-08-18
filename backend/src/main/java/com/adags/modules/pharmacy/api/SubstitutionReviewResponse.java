package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.application.SubstitutionReviewResult;

public record SubstitutionReviewResponse(
        SubstitutionProposalResponse proposal,
        PrescriptionLineResponse cancelledLine,
        PrescriptionLineResponse replacementLine
) {

    public static SubstitutionReviewResponse from(SubstitutionReviewResult result) {
        return new SubstitutionReviewResponse(
                SubstitutionProposalResponse.from(result.proposal()),
                result.cancelledLine() == null ? null : PrescriptionLineResponse.from(result.cancelledLine()),
                result.replacementLine() == null ? null : PrescriptionLineResponse.from(result.replacementLine()));
    }
}
