package com.adags.shared.kernel;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DecimalQuantityTest {
    @Test
    void physicalArithmeticIsDecimalAndDoesNotRoundUnsupportedPrecision() {
        assertThat(DecimalQuantity.multiply(3, 0.1)).isEqualTo(0.3);
        assertThat(DecimalQuantity.subtract(0.3, 0.1)).isEqualTo(0.2);
        assertThatThrownBy(() -> DecimalQuantity.require(0.0000001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecimalQuantity.require(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecimalQuantity.requireForUnit(0.5, "TABLET")).isInstanceOf(IllegalArgumentException.class);
        assertThat(DecimalQuantity.requireForUnit(12.5, "G")).isEqualTo(12.5);
    }

    @Test
    void pricingFractionalGramsUsesDecimalMultiplicationAndWholeMinorUnits() {
        assertThat(new Money(80, "TZS").times(6.25)).isEqualTo(new Money(500, "TZS"));
        assertThat(new Money(3, "TZS").times(0.5)).isEqualTo(new Money(2, "TZS"));
    }
}
