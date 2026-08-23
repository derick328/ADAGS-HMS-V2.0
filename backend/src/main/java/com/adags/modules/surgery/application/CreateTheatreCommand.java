package com.adags.modules.surgery.application;

import java.util.UUID;

public record CreateTheatreCommand(UUID facilityId, String name) {
}
