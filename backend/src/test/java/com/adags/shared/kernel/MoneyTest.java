package com.adags.shared.kernel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 01-architecture.md §9.1: "no raw arithmetic on amounts anywhere else."
class MoneyTest {

    @Test
    void plusAddsWithinTheSameCurrency() {
        Money result = new Money(1000, "TZS").plus(new Money(500, "TZS"));
        assertThat(result).isEqualTo(new Money(1500, "TZS"));
    }

    @Test
    void minusSubtractsWithinTheSameCurrency() {
        Money result = new Money(1000, "TZS").minus(new Money(400, "TZS"));
        assertThat(result).isEqualTo(new Money(600, "TZS"));
    }

    @Test
    void timesMultipliesByAQuantity() {
        Money result = new Money(10_000, "TZS").times(3);
        assertThat(result).isEqualTo(new Money(30_000, "TZS"));
    }

    @Test
    void isGreaterThanComparesWithinTheSameCurrency() {
        assertThat(new Money(1000, "TZS").isGreaterThan(new Money(999, "TZS"))).isTrue();
        assertThat(new Money(999, "TZS").isGreaterThan(new Money(1000, "TZS"))).isFalse();
    }

    @Test
    void combiningDifferentCurrenciesIsRejected() {
        assertThatThrownBy(() -> new Money(1000, "TZS").plus(new Money(1000, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBlankCurrencyCodeIsRejected() {
        assertThatThrownBy(() -> new Money(1000, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
