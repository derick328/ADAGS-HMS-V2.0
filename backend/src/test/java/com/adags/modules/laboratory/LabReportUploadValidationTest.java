package com.adags.modules.laboratory;

import com.adags.modules.laboratory.application.MachineOutputStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabReportUploadValidationTest {
    private final MachineOutputStorageService storage = new MachineOutputStorageService(null, null);

    @Test
    void rejectsNonPdfContentEvenWhenMimeTypeClaimsPdf() {
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", "not a PDF".getBytes());
        assertThatThrownBy(() -> storage.uploadReportPdf(file))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("valid PDF");
    }

    @Test
    void rejectsOversizedPdf() {
        byte[] content = new byte[10 * 1024 * 1024 + 1];
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", content);
        assertThatThrownBy(() -> storage.uploadReportPdf(file))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 MB");
    }
}
