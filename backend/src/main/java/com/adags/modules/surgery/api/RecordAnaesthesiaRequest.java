package com.adags.modules.surgery.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// 04-*.md §4: "PAPER RECORD" — the frontend must label every view of this
// as PAPER RECORD; this request only carries the pointer fields.
public record RecordAnaesthesiaRequest(
        @NotNull UUID surgeryOrderId,
        boolean noAnaesthesia,
        String anaesthetistNameText,
        String anaesthesiaTypeText,
        String paperRecordReference
) {
}
