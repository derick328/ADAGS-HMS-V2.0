package com.adags.modules.referencedata.api;

import jakarta.validation.constraints.NotBlank;

public record ProposeSynonymRequest(
        @NotBlank String code,
        @NotBlank String term,
        @NotBlank String language
) {
}
