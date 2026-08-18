package com.adags.modules.billing;

import com.adags.modules.billing.domain.ProviderBillingCategory;
import com.adags.modules.billing.domain.ProviderBillingCategoryRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// V19__provider_billing_category.sql: the role_code -> service_item_code
// mapping that replaced BillingFacadeImpl's hardcoded
// DOCTOR_ROLE/SPECIALIST_DOCTOR_ROLE constants. No @AfterEach truncate —
// the seeded DOCTOR/SPECIALIST_DOCTOR rows are what
// SubmitTriageAssessmentIntegrationTest and EndToEndBillingTest's
// consultation-pricing path both depend on (same reasoning
// PriceListEntryRepositoryTest documents for service_item).
class ProviderBillingCategoryRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ProviderBillingCategoryRepository repository;

    @Test
    void aDoctorRoleResolvesToTheGeneralConsultationPrice() {
        ProviderBillingCategory category = repository.findHighestPriorityCurrent(Set.of("DOCTOR")).orElseThrow();

        assertThat(category.serviceItemCode()).isEqualTo("CONSULT_GENERAL");
    }

    @Test
    void aSpecialistDoctorRoleResolvesToTheSpecialistConsultationPrice() {
        ProviderBillingCategory category =
                repository.findHighestPriorityCurrent(Set.of("SPECIALIST_DOCTOR")).orElseThrow();

        assertThat(category.serviceItemCode()).isEqualTo("CONSULT_SPECIALIST");
    }

    // The tension the Phase 3 fix resolves: a provider holding both roles
    // must still resolve deterministically. Priority, a data column, wins
    // — never a code-level judgement about which role is "the acceptable kind."
    @Test
    void aProviderHoldingBothRolesResolvesToTheHigherPriorityOne() {
        ProviderBillingCategory category =
                repository.findHighestPriorityCurrent(Set.of("DOCTOR", "SPECIALIST_DOCTOR")).orElseThrow();

        assertThat(category.serviceItemCode()).isEqualTo("CONSULT_SPECIALIST");
    }

    @Test
    void aRoleWithNoMappingResolvesToNothing() {
        assertThat(repository.findHighestPriorityCurrent(Set.of("NURSE"))).isEmpty();
    }
}
