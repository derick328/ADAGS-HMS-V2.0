package com.adags.modules.laboratory.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

// Plain function, no Spring, no I/O. Picks the one current LabReferenceRange
// that applies to a patient's age/sex from a parameter's candidate rows —
// an exact-sex match wins over ANY at the same age band, same specificity
// preference clinical.domain.Sex.fromPatientGender exists to feed.
public final class LabReferenceRangeResolver {

    private LabReferenceRangeResolver() {
    }

    public static int ageInMonths(LocalDate dateOfBirth, LocalDate asOf) {
        Period age = Period.between(dateOfBirth, asOf);
        return age.getYears() * 12 + age.getMonths();
    }

    public static Optional<LabReferenceRange> resolve(List<LabReferenceRange> candidates, int ageMonths, Sex sex) {
        return candidates.stream()
                .filter(range -> range.ageMinMonths() <= ageMonths
                        && (range.ageMaxMonths() == null || ageMonths <= range.ageMaxMonths()))
                .filter(range -> range.sex() == sex || range.sex() == Sex.ANY)
                .min((a, b) -> Boolean.compare(b.sex() == sex, a.sex() == sex)); // exact sex before ANY
    }
}
