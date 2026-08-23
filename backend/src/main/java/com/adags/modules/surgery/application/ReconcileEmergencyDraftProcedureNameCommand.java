package com.adags.modules.surgery.application;

import java.util.UUID;

public record ReconcileEmergencyDraftProcedureNameCommand(
        UUID surgeryOrderId, String procedureServiceItemCode, UUID actorUserId
) {
}
