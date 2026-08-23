package com.adags.modules.surgery.domain;

import java.time.Instant;
import java.util.UUID;

// 04-*.md §4: "PAPER RECORD" — this row is a pointer to a physical/paper
// chart, not a digital anaesthesia order. anaesthetistNameText/
// anaesthesiaTypeText are free text deliberately: the anaesthetist is not
// necessarily a system user, and the type here is what was actually given,
// not a catalogue selection (contrast SurgeryOrder.anesthesiaType, the
// *planned* type at scheduling).
public record AnaesthesiaPaperRecord(
        UUID id,
        UUID surgeryOrderId,
        boolean noAnaesthesia,
        String anaesthetistNameText,
        String anaesthesiaTypeText,
        String paperRecordReference,
        UUID recordedBy,
        Instant recordedAt
) {

    public static AnaesthesiaPaperRecord record(
            UUID surgeryOrderId, String anaesthetistNameText, String anaesthesiaTypeText,
            String paperRecordReference, UUID recordedBy
    ) {
        if (isBlank(anaesthetistNameText) || isBlank(anaesthesiaTypeText) || isBlank(paperRecordReference)) {
            throw new IllegalArgumentException(
                    "anaesthetistNameText, anaesthesiaTypeText and paperRecordReference are all required.");
        }
        return new AnaesthesiaPaperRecord(null, surgeryOrderId, false, anaesthetistNameText, anaesthesiaTypeText,
                paperRecordReference, recordedBy, null);
    }

    public static AnaesthesiaPaperRecord declareNoAnaesthesia(UUID surgeryOrderId, UUID recordedBy) {
        return new AnaesthesiaPaperRecord(null, surgeryOrderId, true, null, null, null, recordedBy, null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
