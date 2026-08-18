package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.Urgency;

import java.util.UUID;

public record PlaceLabOrderCommand(
        UUID encounterId,
        String labTestCode,
        String clinicalIndication,
        Urgency urgency,
        UUID orderedBy
) {
}
