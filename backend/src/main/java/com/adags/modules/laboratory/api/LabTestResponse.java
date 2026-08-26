package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.VerificationLevel;

import java.util.List;
import java.util.UUID;

// 15-lab-technician.md §5.1/§10: the catalogue the Result Entry form is
// generated from, and the context a Lab Catalogue proposal is written
// against. parameters is empty for a test with none configured — "a test
// approved without parameters generates an empty result form," not an error.
public record LabTestResponse(
        UUID id,
        String code,
        String name,
        String specimenType,
        String containerType,
        int expectedTatHours,
        VerificationLevel verificationLevel,
        boolean producesMachineOutput,
        boolean active,
        List<LabTestParameterResponse> parameters
) {
    public static LabTestResponse from(LabTest test, List<LabTestParameter> parameters) {
        return new LabTestResponse(test.id(), test.code(), test.name(), test.specimenType(), test.containerType(),
                test.expectedTatHours(), test.verificationLevel(), test.producesMachineOutput(), test.isActive(),
                parameters.stream().map(LabTestParameterResponse::from).toList());
    }

    public record LabTestParameterResponse(
            UUID id, String code, String name, String unit, int sortOrder, String method, String loincCode
    ) {
        public static LabTestParameterResponse from(LabTestParameter parameter) {
            return new LabTestParameterResponse(parameter.id(), parameter.code(), parameter.name(),
                    parameter.unit(), parameter.sortOrder(), parameter.method(), parameter.loincCode());
        }
    }
}
