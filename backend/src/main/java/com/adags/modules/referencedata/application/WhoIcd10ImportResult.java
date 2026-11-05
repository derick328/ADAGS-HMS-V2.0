package com.adags.modules.referencedata.application;

public record WhoIcd10ImportResult(String release, int imported, int updated, int skipped, int rejected) {}
