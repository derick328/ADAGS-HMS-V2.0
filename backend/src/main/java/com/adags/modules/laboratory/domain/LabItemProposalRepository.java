package com.adags.modules.laboratory.domain;

public interface LabItemProposalRepository {

    /** Persists the proposal row and its parameter list (child rows) in one call. */
    LabItemProposal insert(LabItemProposal proposal);
}
