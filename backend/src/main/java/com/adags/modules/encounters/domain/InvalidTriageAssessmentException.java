package com.adags.modules.encounters.domain;

// Raised by TriageAssessment.create for any malformed submission — a
// missing required field, a conditional field supplied or omitted against
// its gate, an out-of-range score. One type covers all of these (unlike
// ImplausibleVitalException/VitalReferenceRangeNotConfiguredException in
// the clinical module, which are genuinely different failure modes callers
// react to differently) because every case here is the same reaction: 400,
// fix the submission and resubmit.
public class InvalidTriageAssessmentException extends RuntimeException {

    public InvalidTriageAssessmentException(String message) {
        super(message);
    }
}
