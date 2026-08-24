package com.adags.modules.laboratory;

import com.adags.modules.laboratory.domain.LabItemProposal;
import com.adags.modules.laboratory.domain.LabItemProposalParameter;
import com.adags.modules.laboratory.domain.LabItemProposalStatus;
import com.adags.shared.kernel.Money;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 15-lab-technician.md §10: Lab Catalogue proposals. Plain JUnit, no Spring
// context — backend/CLAUDE.md "Testing": domain is unit-tested without one.
class LabItemProposalTest {

    private static final Money PRICE = new Money(4_000, "TZS");

    @Test
    void proposingWithParametersStartsProposedWithNoReviewer() {
        LabItemProposal proposal = LabItemProposal.propose(
                "Full Blood Count", "HAEMATOLOGY", PRICE, "requested by ward round", "BLOOD", "EDTA_TUBE", 24,
                "Ward doctors keep sending FBC to the referral lab", UUID.randomUUID(),
                List.of(LabItemProposalParameter.create("WBC", "x10^9/L", "Automated", 0)));

        assertThat(proposal.status()).isEqualTo(LabItemProposalStatus.PROPOSED);
        assertThat(proposal.reviewedBy()).isNull();
        assertThat(proposal.reviewedAt()).isNull();
        assertThat(proposal.createdLabTestId()).isNull();
        assertThat(proposal.parameters()).hasSize(1);
    }

    // §10: "A test approved without parameters generates an empty result
    // form" — a purely manual/qualitative test proposal is valid with zero parameters.
    @Test
    void proposingWithNoParametersIsValid() {
        LabItemProposal proposal = LabItemProposal.propose(
                "Stool Microscopy", "MICROSCOPY", PRICE, null, "STOOL", "STOOL_CONTAINER", 4,
                "Manual microscopy, no coded parameters yet", UUID.randomUUID(), List.of());

        assertThat(proposal.parameters()).isEmpty();
    }

    @Test
    void proposingWithoutJustificationIsRejected() {
        assertThatThrownBy(() -> LabItemProposal.propose(
                "Full Blood Count", "HAEMATOLOGY", PRICE, null, "BLOOD", "EDTA_TUBE", 24,
                "  ", UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proposingWithoutPriceIsRejected() {
        assertThatThrownBy(() -> LabItemProposal.propose(
                "Full Blood Count", "HAEMATOLOGY", null, null, "BLOOD", "EDTA_TUBE", 24,
                "justification", UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proposingWithZeroOrNegativeTatIsRejected() {
        assertThatThrownBy(() -> LabItemProposal.propose(
                "Full Blood Count", "HAEMATOLOGY", PRICE, null, "BLOOD", "EDTA_TUBE", 0,
                "justification", UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
