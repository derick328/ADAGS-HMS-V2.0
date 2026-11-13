package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.DispenseLine;
import com.adags.shared.kernel.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DispenseLineResponseTest {
    @Test
    void unknownSupplierCostIsNotExposedAsZero() {
        DispenseLine line = new DispenseLine(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 25, new Money(0, "TZS"), null, Instant.now(),
                25, "TABLET", 1, false);
        assertThat(DispenseLineResponse.from(line).unitCostAmountMinor()).isNull();
    }
}
