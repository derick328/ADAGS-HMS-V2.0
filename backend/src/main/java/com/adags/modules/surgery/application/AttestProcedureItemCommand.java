package com.adags.modules.surgery.application;

import java.util.UUID;

public record AttestProcedureItemCommand(UUID procedureItemId, UUID attestedBy) {
}
