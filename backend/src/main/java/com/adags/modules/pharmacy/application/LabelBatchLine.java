package com.adags.modules.pharmacy.application;

import java.time.LocalDate;

public record LabelBatchLine(String batchNumber, LocalDate expiryDate) {
}
