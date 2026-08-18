package com.adags.modules.encounters.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// 05a-receptionist-decisions.md §2 ("final for v1" — supersedes
// 01-architecture.md §2's earlier REGISTERED/TRIAGED naming) plus
// CANCELLED_IN_ERROR (05-receptionist.md §5.3) and the inpatient tail
// (01-architecture.md §2.1: ADMITTED -> IN_WARD -> DISCHARGE_ORDERED ->
// DISCHARGED).
//
// Every row of the transition table lives here, including the ones no
// application service calls yet — doctor, orders, lab and inpatient
// modules don't exist. Modelling the full graph now means later phases
// add a caller, not redesign this. Reachable today: AWAITING_TRIAGE's row
// (registration/triage/dismiss), and AWAITING_PAYMENT -> AWAITING_CONSULT
// / ABANDONED_UNPAID (Phase 3 items 5.2/5.4 — MoveToAwaitingConsultService,
// AbandonUnpaidEncounterService). Everything from AWAITING_CONSULT onward
// still has no caller.
public enum EncounterState {
    AWAITING_TRIAGE,
    AWAITING_PAYMENT,
    AWAITING_CONSULT,
    IN_CONSULT,
    AWAITING_RESULTS,
    CONSULT_COMPLETE,
    PENDING_SETTLEMENT,
    ADMITTED,
    IN_WARD,
    DISCHARGE_ORDERED,
    LEFT_WITHOUT_BEING_SEEN,
    ABANDONED_UNPAID,
    CANCELLED_IN_ERROR,
    REFERRED_OUT,
    DECEASED,
    CLOSED,
    DISCHARGED;

    private static final Set<EncounterState> TERMINAL = EnumSet.of(
            LEFT_WITHOUT_BEING_SEEN, ABANDONED_UNPAID, CANCELLED_IN_ERROR,
            REFERRED_OUT, DECEASED, CLOSED, DISCHARGED);

    private static final Map<EncounterState, Set<EncounterState>> ALLOWED_TRANSITIONS = buildTransitionGraph();

    private static Map<EncounterState, Set<EncounterState>> buildTransitionGraph() {
        Map<EncounterState, Set<EncounterState>> graph = new EnumMap<>(EncounterState.class);
        graph.put(AWAITING_TRIAGE, EnumSet.of(
                AWAITING_PAYMENT, AWAITING_CONSULT, LEFT_WITHOUT_BEING_SEEN, CANCELLED_IN_ERROR, CLOSED));
        graph.put(AWAITING_PAYMENT, EnumSet.of(AWAITING_CONSULT, ABANDONED_UNPAID));
        graph.put(AWAITING_CONSULT, EnumSet.of(IN_CONSULT));
        graph.put(IN_CONSULT, EnumSet.of(AWAITING_RESULTS, CONSULT_COMPLETE, ADMITTED, REFERRED_OUT, DECEASED));
        graph.put(AWAITING_RESULTS, EnumSet.of(AWAITING_CONSULT));
        graph.put(CONSULT_COMPLETE, EnumSet.of(PENDING_SETTLEMENT));
        graph.put(PENDING_SETTLEMENT, EnumSet.of(CLOSED));
        graph.put(ADMITTED, EnumSet.of(IN_WARD));
        // DISCHARGE_ORDERED -> IN_WARD: 04-procedure-bedday-rules.md §3.9,
        // reversing a discharge order. Absent from the original graph
        // (Phase 2 only modelled the forward path); added in Phase 6 item 3,
        // the first caller of this edge.
        graph.put(IN_WARD, EnumSet.of(DISCHARGE_ORDERED));
        graph.put(DISCHARGE_ORDERED, EnumSet.of(DISCHARGED, IN_WARD));
        return graph;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(EncounterState target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
