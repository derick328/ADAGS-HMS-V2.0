package com.adags.modules.clinical.infrastructure;

import com.adags.modules.clinical.domain.AgeBand;
import com.adags.modules.clinical.domain.Sex;
import com.adags.modules.clinical.domain.VitalReferenceRange;
import com.adags.modules.clinical.domain.VitalReferenceRangeRepository;
import com.adags.modules.clinical.domain.VitalType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

// Does not block startup — verifies run() completes normally whether or
// not unconfigured bands exist. Content of the warning is exercised
// implicitly by every other test in the suite booting the full Spring
// context (this bean is real there) without failure.
class ClinicalStartupChecksTest {

    @Test
    void doesNotThrowWhenBandsAreUnconfigured() {
        VitalReferenceRangeRepository repository = new FakeRepository(List.of(
                new VitalReferenceRange(UUID.randomUUID(), VitalType.PULSE, AgeBand.NEONATE, Sex.ANY,
                        null, null, null, null, LocalDate.now(), null)));
        ClinicalStartupChecks checks = new ClinicalStartupChecks(repository);

        assertThatCode(() -> checks.run(null)).doesNotThrowAnyException();
    }

    @Test
    void doesNotThrowWhenNothingIsUnconfigured() {
        ClinicalStartupChecks checks = new ClinicalStartupChecks(new FakeRepository(List.of()));

        assertThatCode(() -> checks.run(null)).doesNotThrowAnyException();
    }

    private record FakeRepository(List<VitalReferenceRange> unconfigured) implements VitalReferenceRangeRepository {
        @Override
        public Optional<VitalReferenceRange> findCurrent(VitalType vitalType, AgeBand ageBand, Sex sex) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public List<VitalReferenceRange> findUnconfigured() {
            return unconfigured;
        }
    }
}
