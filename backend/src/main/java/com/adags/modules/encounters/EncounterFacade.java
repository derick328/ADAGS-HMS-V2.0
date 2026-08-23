package com.adags.modules.encounters;

import com.adags.shared.kernel.EncounterContext;

import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Returns shared/kernel vocabulary, never
// com.adags.modules.encounters.domain.Encounter — first caller is the
// laboratory module, which needs an order's patient/facility/department/
// payer type without importing the Encounter entity across the boundary.
public interface EncounterFacade {

    Optional<EncounterContext> findContext(UUID encounterId);

    /**
     * 07-doctor.md §7's ADMITTED disposition: IN_CONSULT -> ADMITTED.
     * Throws InvalidEncounterTransitionException if the encounter's current
     * state does not allow it. The inpatient module calls this rather than
     * importing EncounterState — same reasoning as every other cross-module
     * write in this codebase going through a facade method, not the entity.
     */
    void admit(UUID encounterId, UUID actorUserId);

    /**
     * 17-ward-nurse.md §1's resolution table: ADMITTED -> IN_WARD, called on
     * an admission's first bed assignment only — a transfer's later
     * assignments must not call this again.
     */
    void moveToWard(UUID encounterId, UUID actorUserId);

    /** 04-procedure-bedday-rules.md §3.6: IN_WARD -> DISCHARGE_ORDERED. */
    void orderDischarge(UUID encounterId, UUID actorUserId);

    /**
     * §3.9: DISCHARGE_ORDERED -> IN_WARD, reversing a discharge order.
     * reason is required by the caller (MissingReversalReasonException,
     * inpatient's own check) and recorded on the EncounterEvent this
     * writes — "without this path, staff delete the record and re-admit,
     * which fragments the encounter."
     */
    void reverseDischargeOrder(UUID encounterId, UUID actorUserId, String reason);

    /**
     * §3.6/root CLAUDE.md "Billing": DISCHARGE_ORDERED -> DISCHARGED,
     * terminal. Never conditioned on payment — there is no code path
     * where an unpaid balance blocks this call.
     */
    void completeDischarge(UUID encounterId, UUID actorUserId);

    /**
     * Phase 8 item 5: IN_CONSULT -> CONSULT_COMPLETE for the surgery
     * module's RECOVERY_THEN_DISCHARGE disposition. Deliberately narrow —
     * unlike FinalizeConsultationService (the doctor's own disposition
     * flow), this does not touch ClinicalFacade/ConsultationRecord; a
     * surgical episode's documentation is the SurgeryOrder/ProcedureItem/
     * checklist chain, not a consultation note.
     */
    void moveToConsultComplete(UUID encounterId, UUID actorUserId);

    /** Phase 8 item 5: IN_CONSULT -> REFERRED_OUT (terminal) for TRANSFER_OUT disposition. */
    void referOut(UUID encounterId, UUID actorUserId);

    /** Phase 8 item 5: IN_CONSULT -> DECEASED (terminal) for DECEASED disposition. */
    void markDeceased(UUID encounterId, UUID actorUserId);
}
