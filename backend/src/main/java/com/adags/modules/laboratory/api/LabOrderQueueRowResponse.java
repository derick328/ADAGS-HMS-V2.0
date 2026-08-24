package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.SpecimenQuality;
import com.adags.modules.laboratory.domain.SpecimenStatus;
import com.adags.modules.laboratory.domain.Urgency;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §3: one flat row per open lab order, everything a
// tab needs to bucket and sort it. The frontend buckets into the five
// sub-tabs (ready to process / in progress / awaiting payment / emergency /
// overdue) client-side, same as every other multi-tab QueueShell screen in
// this codebase — no server-side tab parameter.
public record LabOrderQueueRowResponse(
        UUID labOrderId,
        UUID encounterId,
        UUID patientId,
        String patientName,
        String hospitalNumber,
        String labTestCode,
        String labTestName,
        String specimenType,
        String containerType,
        int expectedTatHours,
        Urgency urgency,
        Instant orderedAt,
        boolean gateSatisfied,
        boolean isEmergencyBypass,
        UUID specimenId,
        String accessionNo,
        SpecimenStatus specimenStatus,
        SpecimenQuality specimenQuality,
        Instant acceptedAt,
        boolean overdue
) {
}
