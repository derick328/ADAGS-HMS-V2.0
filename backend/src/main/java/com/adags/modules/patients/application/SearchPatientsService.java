package com.adags.modules.patients.application;

import com.adags.modules.patients.domain.PatientListPage;
import com.adags.modules.patients.domain.PatientListResult;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.modules.patients.domain.PatientSearchResult;
import com.adags.shared.authz.AuditRecorder;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    // The list is pageable but each page stays bounded: an unbounded page
    // size would turn the pager into the "list all patients" screen
    // 05-receptionist.md §5.1 rules out.
    private static final int MAX_PAGE_SIZE = 50;

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

    // Admin Patients page and the doctor workspace's "All patients" tab both
    // open on this. Same audit event key as search(): a browse of the
    // registration list is a query over the same data, so distinguishing
    // event types in the audit log would be cosmetic. Every page fetch is a
    // separate audit row — paging through the list leaves a trail per page,
    // which is the point.
    public PatientListPage listRecent(Instant from, Instant to, String payerFilter,
                                      int requestedPage, int requestedPageSize, UUID actorUserId) {
        int pageSize = Math.min(Math.max(requestedPageSize, 1), MAX_PAGE_SIZE);
        int page = Math.max(requestedPage, 0);
        auditRecorder.record(actorUserId, "PATIENT_SEARCH", "PATIENT", null, null);
        long total = patientRepository.countRecent(from, to, payerFilter);
        List<PatientListResult> items =
                patientRepository.listRecent(from, to, payerFilter, page * pageSize, pageSize);
        return new PatientListPage(page, pageSize, total, items);
    }
}
