package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// root CLAUDE.md "Billing": "Nobody types a price... A missing price for
// the applicable payer blocks with an explicit error — it never defaults
// to zero." Exercised against the real V15-seeded rows.
class PriceResolutionServiceTest extends IntegrationTestSupport {

    @Autowired
    private PriceResolutionService service;

    @Test
    void resolvesTheSeededCashConsultGeneralPrice() {
        Money price = service.resolvePrice("CONSULT_GENERAL", PayerCategory.CASH);
        assertThat(price).isEqualTo(new Money(8_000, "TZS"));
    }

    @Test
    void resolvesTheSeededCashConsultSpecialistPrice() {
        Money price = service.resolvePrice("CONSULT_SPECIALIST", PayerCategory.CASH);
        assertThat(price).isEqualTo(new Money(30_000, "TZS"));
    }

    @Test
    void resolvesTheClinicalOfficerFeeAcrossPayerCategories() {
        assertThat(service.resolvePrice("CONSULT_CLINICAL_OFFICER", PayerCategory.CASH))
                .isEqualTo(new Money(4_000, "TZS"));
        assertThat(service.resolvePrice("CONSULT_CLINICAL_OFFICER", PayerCategory.NHIF))
                .isEqualTo(new Money(4_000, "TZS"));
    }

    @Test
    void anUnknownServiceItemCodeBlocksWithAnExplicitError() {
        assertThatThrownBy(() -> service.resolvePrice("NO_SUCH_ITEM", PayerCategory.CASH))
                .isInstanceOf(PriceNotConfiguredException.class)
                .hasMessageContaining("NO_SUCH_ITEM");
    }
}
