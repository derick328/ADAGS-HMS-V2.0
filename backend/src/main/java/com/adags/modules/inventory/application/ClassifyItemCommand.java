package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.RegulatorySchedule;

import java.util.UUID;

public record ClassifyItemCommand(UUID itemId, RegulatorySchedule newSchedule, String reason, UUID actorUserId) {
}
