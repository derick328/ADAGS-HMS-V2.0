package com.adags.modules.laboratory.domain;

import java.util.UUID;

// Mirrors clinical.domain.VitalReferenceRangeNotConfiguredException: no
// implausible/normal bounds for this parameter at this patient's age/sex
// means there is nothing safe to evaluate against — refuse rather than
// silently skip the check or fall back to an adjacent band.
public class LabReferenceRangeNotConfiguredException extends RuntimeException {

    public LabReferenceRangeNotConfiguredException(UUID parameterId, int ageMonths, Sex sex) {
        super("No configured reference range for parameter " + parameterId
                + " at age " + ageMonths + " months, sex " + sex + ".");
    }
}
