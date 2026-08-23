package com.adags.shared.kernel;

import java.util.UUID;

/** IdentityFacade's transfer vocabulary — same reasoning as PatientIdentity/PatientDemographics: shared/kernel, never a modules.identity type, so a caller crosses no module boundary to use it. */
public record UserRef(UUID userId, String username) {
}
