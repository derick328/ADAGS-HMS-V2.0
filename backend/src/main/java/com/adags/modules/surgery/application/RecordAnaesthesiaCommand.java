package com.adags.modules.surgery.application;

import java.util.UUID;

public record RecordAnaesthesiaCommand(
        UUID surgeryOrderId,
        boolean noAnaesthesia,
        String anaesthetistNameText,
        String anaesthesiaTypeText,
        String paperRecordReference,
        UUID recordedBy
) {
}
