package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

// Phase 8 item 8: "48h -> CLINICAL_LEAD governance queue. Never
// auto-closed." This publishes an escalation event per stale draft
// through the transactional outbox (backend/CLAUDE.md: "Domain events
// published through the transactional outbox, never a direct call") —
// it never transitions status. Nothing in this codebase auto-closes a
// DRAFT_EMERGENCY order; only CancelSurgeryOrderService or
// ReconcileEmergencyDraftProcedureNameService (via the surgeon) ever move
// it, and both require an explicit call.
@Service
public class EmergencyDraftGovernanceService {

    private static final Duration GOVERNANCE_THRESHOLD = Duration.ofHours(48);

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public EmergencyDraftGovernanceService(
            SurgeryOrderRepository surgeryOrderRepository, DomainEventPublisher eventPublisher, Clock clock
    ) {
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void escalateStaleDrafts() {
        Instant boundary = Instant.now(clock).minus(GOVERNANCE_THRESHOLD);
        for (SurgeryOrder order : surgeryOrderRepository.findStaleDrafts(boundary)) {
            if (surgeryOrderRepository.markGovernanceEscalated(order.id())) {
                eventPublisher.publish("EmergencyDraftGovernanceEscalation", Map.of(
                        "surgeryOrderId", order.id().toString(),
                        "encounterId", order.encounterId().toString(),
                        "scheduledAt", order.scheduledAt().toString()));
            }
        }
    }
}
