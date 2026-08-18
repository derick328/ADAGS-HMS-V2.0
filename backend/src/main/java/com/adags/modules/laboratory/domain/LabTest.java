package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §5.1: the catalogue root the result form (phase 5)
// generates from. code is expected to match a billing service_item.code —
// see V20__lab_catalogue.sql for why there is no DB FK for that.
public record LabTest(
        UUID id,
        String code,
        String name,
        String specimenType,
        String containerType,
        int expectedTatHours,
        VerificationLevel verificationLevel,
        boolean producesMachineOutput,
        boolean isActive,
        Instant createdAt
) {

    public static LabTest create(String code, String name, String specimenType, String containerType,
                                  int expectedTatHours, VerificationLevel verificationLevel,
                                  boolean producesMachineOutput) {
        return new LabTest(null, code, name, specimenType, containerType, expectedTatHours,
                verificationLevel, producesMachineOutput, true, null);
    }
}
