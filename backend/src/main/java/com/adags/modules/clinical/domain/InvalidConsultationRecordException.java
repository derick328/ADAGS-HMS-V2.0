package com.adags.modules.clinical.domain;

// Raised by FinalizeConsultationRecordService when a required field
// (09-doctor-incomplete-consultations.md §5.1) is missing — one type for
// every case, same reasoning as InvalidTriageAssessmentException: each is
// the same reaction (400, fix the submission and resubmit), not genuinely
// different failure modes a caller reacts to differently.
public class InvalidConsultationRecordException extends RuntimeException {

    public InvalidConsultationRecordException(String message) {
        super(message);
    }
}
