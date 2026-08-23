package com.adags.modules.surgery.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// Phase 8 item 1's four states plus item 8's DRAFT_EMERGENCY. Transition
// graph modelled the same way EncounterState models its full graph ahead
// of every caller.
//
// DRAFT_EMERGENCY -> IN_PROGRESS is ReconcileEmergencyDraftProcedureNameService
// (item 8: once the free-text procedure name is reconciled to a catalogue
// item and the charge is posted, the draft rejoins the normal pipeline —
// checklist/reconciliation/completion apply exactly as they do to a
// normally-scheduled order from that point on). DRAFT_EMERGENCY ->
// CANCELLED exists for a draft that turns out to have been created in
// error; task text: "Never auto-closed" — nothing in this codebase drives
// that edge automatically, only an explicit cancel call.
public enum SurgeryOrderStatus {
    DRAFT_EMERGENCY,
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Set<SurgeryOrderStatus> TERMINAL = EnumSet.of(COMPLETED, CANCELLED);

    private static final Map<SurgeryOrderStatus, Set<SurgeryOrderStatus>> ALLOWED_TRANSITIONS = buildTransitionGraph();

    private static Map<SurgeryOrderStatus, Set<SurgeryOrderStatus>> buildTransitionGraph() {
        Map<SurgeryOrderStatus, Set<SurgeryOrderStatus>> graph = new EnumMap<>(SurgeryOrderStatus.class);
        graph.put(DRAFT_EMERGENCY, EnumSet.of(IN_PROGRESS, CANCELLED));
        graph.put(SCHEDULED, EnumSet.of(IN_PROGRESS, CANCELLED));
        graph.put(IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED));
        return graph;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(SurgeryOrderStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
