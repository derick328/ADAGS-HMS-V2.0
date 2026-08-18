package com.adags.modules.billing.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    /**
     * @throws CashierShiftNotOpenException if the shift is not OPEN at the moment of insert
     *         (root CLAUDE.md "Concurrency": the guard is in the INSERT's WHERE, not a
     *         read-then-write check — a shift closing between validation and this call
     *         is caught here too).
     */
    Payment insert(Payment payment);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findById(UUID id);
}
