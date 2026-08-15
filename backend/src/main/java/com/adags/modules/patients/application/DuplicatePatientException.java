package com.adags.modules.patients.application;

import com.adags.modules.patients.domain.DuplicateCandidate;

import java.util.List;

public class DuplicatePatientException extends RuntimeException {

    private final List<DuplicateCandidate> candidates;

    public DuplicatePatientException(List<DuplicateCandidate> candidates) {
        super("Registration blocked by " + candidates.size() + " duplicate candidate(s)");
        this.candidates = candidates;
    }

    public List<DuplicateCandidate> candidates() {
        return candidates;
    }
}
