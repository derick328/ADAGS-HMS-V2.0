package com.adags.modules.laboratory.application;

import java.util.UUID;

public record VerifyLabResultCommand(UUID resultId, UUID verifiedBy, String verificationComment) {
}
