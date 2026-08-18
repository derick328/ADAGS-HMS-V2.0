package com.adags.modules.encounters.domain;

// 07-doctor.md §7's disposition table. Only the three outcomes item 5
// actually wires up are here — REFERRED_DOCTOR/REFERRED_SPECIALIST (§7.1:
// same encounter, reassigned provider, new charge), ADMITTED and
// SCHEDULE_SURGERY are deliberately excluded rather than added as
// unhandled enum literals: unlike EncounterState (which models the whole
// graph because canTransitionTo is cheap to leave unreachable), a
// DispositionOutcome value implies FinalizeConsultationService knows how
// to execute it, and internal referral in particular needs its own
// charge-posting flow this item doesn't build.
public enum DispositionOutcome {
    CONSULT_COMPLETE,
    REFERRED_OUT,
    DECEASED
}
