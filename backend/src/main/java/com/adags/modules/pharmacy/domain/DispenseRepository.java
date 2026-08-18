package com.adags.modules.pharmacy.domain;

import java.util.Optional;
import java.util.UUID;

public interface DispenseRepository {

    Dispense insert(Dispense dispense);

    Optional<Dispense> findById(UUID id);

    /** §5.4/§5.5: a repeat with the same key returns the original, never a second dispense — same PostPaymentService pattern. */
    Optional<Dispense> findByIdempotencyKey(String idempotencyKey);
}
