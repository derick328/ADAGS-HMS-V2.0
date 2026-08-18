package com.adags.modules.pharmacy.application;

import com.adags.modules.pharmacy.domain.SubstitutionStatus;

import java.util.UUID;

public record ReviewSubstitutionCommand(UUID proposalId, SubstitutionStatus decision, UUID reviewedBy, String reviewNote) {
}
