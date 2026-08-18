package com.adags.modules.referencedata.api;

import com.adags.modules.referencedata.application.Icd10ImportRow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ImportIcd10ReleaseRequest(
        @NotBlank String release,
        @NotEmpty List<Icd10ImportRow> codes
) {
}
