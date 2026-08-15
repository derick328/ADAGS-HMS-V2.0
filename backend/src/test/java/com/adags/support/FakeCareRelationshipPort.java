package com.adags.support;

import com.adags.shared.authz.CareRelationshipPort;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FakeCareRelationshipPort implements CareRelationshipPort {

    private record Pair(UUID actorUserId, UUID patientId) {
    }

    private final Set<Pair> relationships = new HashSet<>();

    public void grant(UUID actorUserId, UUID patientId) {
        relationships.add(new Pair(actorUserId, patientId));
    }

    @Override
    public boolean hasCareRelationship(UUID actorUserId, UUID patientId) {
        return relationships.contains(new Pair(actorUserId, patientId));
    }
}
