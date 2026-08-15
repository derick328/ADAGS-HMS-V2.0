package com.adags.shared.authz.infrastructure;

import com.adags.shared.authz.CareRelationshipPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Phase 0 has no Patient or Encounter table to check against. Superseded by
// the encounters module's real implementation in Phase 1 — see CareRelationshipPort.
@Component
public class NoCareRelationshipPort implements CareRelationshipPort {

    @Override
    public boolean hasCareRelationship(UUID actorUserId, UUID patientId) {
        return false;
    }
}
