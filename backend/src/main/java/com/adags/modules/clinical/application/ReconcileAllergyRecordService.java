package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.AllergyConfirmationStatus;
import com.adags.modules.clinical.domain.AllergyRecordRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 07-doctor.md §6.1: "The doctor confirms or refutes what triage recorded."
// Updates the existing row's confirmation metadata in place — see
// AllergyRecord's class comment for why this doesn't need a new row the
// way the clinical content itself would. §6.1: "refuting requires a note" —
// enforced here for a clean error, and by the allergy_record CHECK
// constraint (V10 migration) independent of this service.
@Service
public class ReconcileAllergyRecordService {

    private final AllergyRecordRepository repository;
    private final Clock clock;

    public ReconcileAllergyRecordService(AllergyRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void reconcile(UUID allergyRecordId, AllergyConfirmationStatus decision, UUID confirmedBy, String confirmationNote) {
        if (decision != AllergyConfirmationStatus.CONFIRMED && decision != AllergyConfirmationStatus.REFUTED) {
            throw new IllegalArgumentException("A reconciliation decision must be CONFIRMED or REFUTED, not " + decision + ".");
        }
        if (decision == AllergyConfirmationStatus.REFUTED && (confirmationNote == null || confirmationNote.isBlank())) {
            throw new IllegalArgumentException("Refuting an allergy record requires a note.");
        }
        repository.findById(allergyRecordId)
                .orElseThrow(() -> new NoSuchElementException("AllergyRecord not found: " + allergyRecordId));

        repository.reconcile(allergyRecordId, decision, confirmedBy, Instant.now(clock), confirmationNote);
    }
}
