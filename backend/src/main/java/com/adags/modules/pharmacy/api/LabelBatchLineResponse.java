package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.application.LabelBatchLine;

import java.time.LocalDate;

public record LabelBatchLineResponse(String batchNumber, LocalDate expiryDate) {

    public static LabelBatchLineResponse from(LabelBatchLine line) {
        return new LabelBatchLineResponse(line.batchNumber(), line.expiryDate());
    }
}
