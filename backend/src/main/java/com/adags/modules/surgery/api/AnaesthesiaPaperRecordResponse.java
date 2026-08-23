package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.AnaesthesiaPaperRecord;

import java.time.Instant;
import java.util.UUID;

// "label": always "PAPER RECORD" — 04-*.md §4's display requirement,
// carried in the payload so no frontend view can render this without it.
public record AnaesthesiaPaperRecordResponse(
        UUID id,
        UUID surgeryOrderId,
        String label,
        boolean noAnaesthesia,
        String anaesthetistNameText,
        String anaesthesiaTypeText,
        String paperRecordReference,
        UUID recordedBy,
        Instant recordedAt
) {
    public static AnaesthesiaPaperRecordResponse from(AnaesthesiaPaperRecord record) {
        return new AnaesthesiaPaperRecordResponse(record.id(), record.surgeryOrderId(), "PAPER RECORD",
                record.noAnaesthesia(), record.anaesthetistNameText(), record.anaesthesiaTypeText(),
                record.paperRecordReference(), record.recordedBy(), record.recordedAt());
    }
}
