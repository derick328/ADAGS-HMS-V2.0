package com.adags.modules.laboratory.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

// 16-lab-verification-model.md v0.2 §2: "result_version_hash — what was
// approved." A verifier's signature covers the exact parameter values they
// checked; a later amendment produces a new result row (and so a new
// hash), never mutates this one.
public final class ResultVersionHash {

    private ResultVersionHash() {
    }

    public static String compute(List<LabResultParameterValue> values) {
        String canonical = values.stream()
                .sorted(Comparator.comparing(v -> v.parameterId().toString()))
                .map(v -> v.parameterId() + ":" + v.resultValue() + ":" + v.flag())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
