package com.adags.modules.surgery.domain;

import java.time.Instant;
import java.util.UUID;

// Deliberately minimal — root CLAUDE.md "Facility": a physical resource,
// so it carries facility_id (unlike patient/definition tables). active is
// the soft-deactivation flag every other admin-defined resource (Ward,
// Item) carries instead of a hard delete.
public record Theatre(
        UUID id,
        UUID facilityId,
        String name,
        boolean active,
        Instant createdAt
) {
}
