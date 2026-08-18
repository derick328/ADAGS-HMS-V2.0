package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.MisclassificationReport;

import java.util.List;

public record MisclassificationReportResponse(
        List<MisclassificationCandidateResponse> candidates,
        List<ControlledReleaseResponse> controlledReleases
) {

    public static MisclassificationReportResponse from(MisclassificationReport report) {
        return new MisclassificationReportResponse(
                report.candidates().stream().map(MisclassificationCandidateResponse::from).toList(),
                report.controlledReleases().stream().map(ControlledReleaseResponse::from).toList());
    }
}
