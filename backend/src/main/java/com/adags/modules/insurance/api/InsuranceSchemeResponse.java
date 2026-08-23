package com.adags.modules.insurance.api;

import com.adags.modules.insurance.domain.InsuranceScheme;

import java.util.UUID;

public record InsuranceSchemeResponse(UUID id, String code, String name) {

    public static InsuranceSchemeResponse from(InsuranceScheme scheme) {
        return new InsuranceSchemeResponse(scheme.id(), scheme.code(), scheme.name());
    }
}
