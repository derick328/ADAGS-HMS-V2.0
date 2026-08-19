package com.adags.shared.kernel;

import java.util.UUID;

// Phase 7 item 3: what InsuranceFacade.resolveCoverageSplit hands back to
// billing — the gross/insurance/patient split ChargePostingService writes
// onto InvoiceLine, plus which SchemeCoverageRule produced it
// (coverageRuleId), same shared.kernel DTO pattern as WardVitalsResult.
public record CoverageSplit(Money insurancePortion, Money patientPortion, UUID coverageRuleId) {
}
