package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.SpecimenQuality;

import java.util.List;
import java.util.UUID;

// 15-lab-technician.md §5.1/§5.2: the generated Result Entry form. The
// technician enters resultValue only — everything else here is read-only
// context resolved server-side, same invariant as flag computation:
// nothing about ranges or identity is typed twice.
public record ResultEntryFormResponse(
        UUID labOrderId,
        UUID specimenId,
        String accessionNo,
        SpecimenQuality specimenQuality,
        String specimenQualityNote,
        String patientName,
        String hospitalNumber,
        Integer patientAgeYears,
        String patientSex,
        String labTestCode,
        String labTestName,
        String clinicalIndication,
        boolean producesMachineOutput,
        List<ParameterFieldResponse> parameters
) {
    public record ParameterFieldResponse(
            UUID parameterId,
            String code,
            String name,
            String unit,
            int sortOrder,
            String method,
            Double normalLow,
            Double normalHigh,
            Double criticalLow,
            Double criticalHigh
    ) {
    }
}
