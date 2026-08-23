package com.adags.modules.surgery.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 8 item 1: "Consent blocks SCHEDULING, not billing. No signed
// document, no theatre slot." Plain JUnit — no Spring context, no I/O
// (backend/CLAUDE.md "Testing": "Domain: plain JUnit, no Spring context").
class SurgeryOrderTest {

    private final UUID encounterId = UUID.randomUUID();
    private final UUID theatreId = UUID.randomUUID();
    private final UUID invoiceLineId = UUID.randomUUID();
    private final UUID scheduledBy = UUID.randomUUID();

    @Test
    void scheduleRejectsWhenConsentNotObtained() {
        assertThatThrownBy(() -> SurgeryOrder.schedule(
                encounterId, "APPENDECTOMY", "Appendectomy", "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now(), "MORNING", theatreId, 60, List.of(), null,
                false, "s3://consents/abc.pdf", invoiceLineId, scheduledBy))
                .isInstanceOf(ConsentNotObtainedException.class);
    }

    @Test
    void scheduleRejectsWhenConsentObtainedButNoDocumentKey() {
        assertThatThrownBy(() -> SurgeryOrder.schedule(
                encounterId, "APPENDECTOMY", "Appendectomy", "GENERAL_SURGERY", SurgeryUrgency.ELECTIVE, "GENERAL",
                LocalDate.now(), "MORNING", theatreId, 60, List.of(), null,
                true, "  ", invoiceLineId, scheduledBy))
                .isInstanceOf(ConsentNotObtainedException.class);
    }

    @Test
    void scheduleSucceedsWithConsentAndDocumentKey() {
        SurgeryOrder order = SurgeryOrder.schedule(
                encounterId, "APPENDECTOMY", "Appendectomy", "GENERAL_SURGERY", SurgeryUrgency.IMMEDIATE, "GENERAL",
                LocalDate.now(), "MORNING", theatreId, 60, List.of(), "pre-op note",
                true, "s3://consents/abc.pdf", invoiceLineId, scheduledBy);

        assertThat(order.id()).isNull();
        assertThat(order.status()).isEqualTo(SurgeryOrderStatus.SCHEDULED);
        assertThat(order.consentObtained()).isTrue();
        assertThat(order.urgency()).isEqualTo(SurgeryUrgency.IMMEDIATE);
        assertThat(order.invoiceLineId()).isEqualTo(invoiceLineId);

        UUID id = UUID.randomUUID();
        assertThat(order.withId(id).id()).isEqualTo(id);
    }
}
