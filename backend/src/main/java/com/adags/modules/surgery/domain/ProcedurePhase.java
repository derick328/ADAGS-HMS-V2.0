package com.adags.modules.surgery.domain;

// 04-procedure-bedday-rules.md §2.1: deferral follows from the phase, and
// nobody marks it — INTRA_OP is "necessarily deferred" (nothing knows an
// item will be used until it has been); PRE_OP/POST_OP are known in
// advance. See ProcedureItem.isDeferred.
public enum ProcedurePhase {
    PRE_OP,
    INTRA_OP,
    POST_OP
}
