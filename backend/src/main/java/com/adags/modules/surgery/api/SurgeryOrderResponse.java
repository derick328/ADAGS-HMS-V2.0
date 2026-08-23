package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.CancellationReason;
import com.adags.modules.surgery.domain.SurgeryDisposition;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.modules.surgery.domain.SurgeryUrgency;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SurgeryOrderResponse(
        UUID id,
        UUID encounterId,
        String procedureServiceItemCode,
        String procedureName,
        String surgeryType,
        SurgeryUrgency urgency,
        String anesthesiaType,
        LocalDate scheduledDate,
        String timeSlot,
        UUID theatreId,
        int estimatedDurationMinutes,
        List<UUID> assignedNurses,
        String preOpNotes,
        boolean consentObtained,
        String consentDocumentKey,
        SurgeryOrderStatus status,
        UUID invoiceLineId,
        UUID scheduledBy,
        Instant scheduledAt,
        SurgeryDisposition disposition,
        UUID dispositionRecordedBy,
        Instant dispositionRecordedAt,
        CancellationReason cancellationReason,
        UUID cancelledBy,
        Instant cancelledAt
) {
    public static SurgeryOrderResponse from(SurgeryOrder order) {
        return new SurgeryOrderResponse(order.id(), order.encounterId(), order.procedureServiceItemCode(),
                order.procedureName(), order.surgeryType(), order.urgency(), order.anesthesiaType(),
                order.scheduledDate(), order.timeSlot(), order.theatreId(), order.estimatedDurationMinutes(),
                order.assignedNurses(), order.preOpNotes(), order.consentObtained(), order.consentDocumentKey(),
                order.status(), order.invoiceLineId(), order.scheduledBy(), order.scheduledAt(),
                order.disposition(), order.dispositionRecordedBy(), order.dispositionRecordedAt(),
                order.cancellationReason(), order.cancelledBy(), order.cancelledAt());
    }
}
