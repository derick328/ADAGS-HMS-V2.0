package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.Theatre;

import java.time.Instant;
import java.util.UUID;

public record TheatreResponse(UUID id, UUID facilityId, String name, boolean active, Instant createdAt) {

    public static TheatreResponse from(Theatre theatre) {
        return new TheatreResponse(theatre.id(), theatre.facilityId(), theatre.name(), theatre.active(),
                theatre.createdAt());
    }
}
