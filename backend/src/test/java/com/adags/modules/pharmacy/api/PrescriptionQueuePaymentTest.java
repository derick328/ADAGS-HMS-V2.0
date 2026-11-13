package com.adags.modules.pharmacy.api;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.InpatientFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.patients.PatientFacade;
import com.adags.modules.pharmacy.application.PlacePrescriptionService;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.authz.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrescriptionQueuePaymentTest {

    @Test
    void unpaidLinesStayOutOfPharmacyUntilTheirOwnPaymentIsRecorded() {
        var orders = mock(PrescriptionOrderRepository.class);
        var lines = mock(PrescriptionLineRepository.class);
        var billing = mock(BillingFacade.class);
        var inpatient = mock(InpatientFacade.class);
        var inventory = mock(InventoryFacade.class);
        var patients = mock(PatientFacade.class);
        var authorization = mock(AuthorizationService.class);
        var actor = new Actor(UUID.randomUUID());
        var orderId = UUID.randomUUID();
        var encounterId = UUID.randomUUID();
        var paidInvoiceLineId = UUID.randomUUID();
        var unpaidInvoiceLineId = UUID.randomUUID();
        var paid = PrescriptionLine.create(orderId, UUID.randomUUID(), 10, "Once daily",
                paidInvoiceLineId, false, null, null, null, null, null);
        var unpaid = PrescriptionLine.create(orderId, UUID.randomUUID(), 10, "Once daily",
                unpaidInvoiceLineId, false, null, null, null, null, null);
        var order = new PrescriptionOrder(orderId, encounterId, UUID.randomUUID(), UUID.randomUUID(),
                actor.userId(), Instant.now(), Instant.now());

        when(authorization.hasPermission(actor, "PRESCRIPTION_QUEUE_READ")).thenReturn(true);
        when(lines.findOpen()).thenReturn(List.of(paid, unpaid));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(inpatient.findActiveAdmissionWard(encounterId)).thenReturn(Optional.empty());
        when(inpatient.findActiveAdmissionId(encounterId)).thenReturn(Optional.empty());
        when(inventory.findItemSummary(any())).thenReturn(Optional.empty());
        when(patients.findIdentity(order.patientId())).thenReturn(Optional.empty());
        when(billing.isLineAuthorized(paidInvoiceLineId)).thenReturn(true);
        when(billing.isLineAuthorized(unpaidInvoiceLineId)).thenReturn(false, true);

        var controller = new PrescriptionController(mock(PlacePrescriptionService.class), orders, lines,
                inventory, billing, mock(EncounterFacade.class), inpatient, patients,
                authorization, mock(AuditRecorder.class));

        assertThat(controller.queue(actor)).extracting(PrescriptionQueueRowResponse::itemId)
                .containsExactly(paid.itemId());
        assertThat(controller.queue(actor)).extracting(PrescriptionQueueRowResponse::itemId)
                .containsExactlyInAnyOrder(paid.itemId(), unpaid.itemId());
    }
}
