package com.adags.modules.patients.application;

import com.adags.modules.patients.domain.PatientRepository;
import com.adags.modules.patients.domain.PatientSearchResult;
import com.adags.shared.authz.AuditRecorder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// 05-receptionist.md §5.1: "There is no legitimate 'list all patients'
// screen ... Unbounded patient browsing is the classic data-exfiltration
// path ... Rate-limit search and audit every query." Rate limiting needs
// infrastructure this codebase doesn't have yet (no request-limiter is
// wired anywhere) — deliberately not built here; only the audit half and
// the server-side result cap are.
@Service
public class SearchPatientsService {

    private static final int MAX_RESULTS = 50;

    private final PatientRepository patientRepository;
    private final AuditRecorder auditRecorder;

    public SearchPatientsService(PatientRepository patientRepository, AuditRecorder auditRecorder) {
        this.patientRepository = patientRepository;
        this.auditRecorder = auditRecorder;
    }

    public List<PatientSearchResult> search(String query, int requestedLimit, UUID actorUserId) {
        int limit = Math.min(Math.max(requestedLimit, 1), MAX_RESULTS);
        auditRecorder.record(actorUserId, "PATIENT_SEARCH", "PATIENT", null, null);
        return patientRepository.search(query, limit);
    }
}
