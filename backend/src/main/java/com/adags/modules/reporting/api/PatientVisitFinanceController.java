package com.adags.modules.reporting.api;

import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operational and financial projection. No clinical tables or narratives enter this response. */
@RestController
@RequestMapping("/api/patient-visits")
public class PatientVisitFinanceController {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationService authorization;

    public PatientVisitFinanceController(NamedParameterJdbcTemplate jdbc, AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.authorization = authorization;
    }

    public record Visit(UUID encounterId, Instant visitedAt, String department, String clinician,
                        String status, List<VisitInvoice> invoices) {}
    public record VisitInvoice(UUID id, String number, String state, String payerCategory, String currencyCode,
                               long grossMinor, long patientPortionMinor, long insurancePortionMinor,
                               long patientPaidMinor, long patientOutstandingMinor, long insuranceOutstandingMinor,
                               List<VisitPayment> payments) {}
    public record VisitPayment(UUID id, long amountMinor, String method, String status,
                               Instant receivedAt, String reference) {}

    @GetMapping("/{patientId}")
    public List<Visit> visits(@PathVariable UUID patientId, @RequestParam UUID facilityId,
                              @AuthenticationPrincipal Actor actor) {
        ScopeRef scope = ScopeRef.of(ScopeType.FACILITY, facilityId);
        if (!authorization.hasPermission(actor, "PATIENT_READ_VISIT_HISTORY", scope)
                || !authorization.hasPermission(actor, "PATIENT_READ_FINANCIAL", scope)) {
            throw new AccessDeniedException("Missing patient visit financial access");
        }
        var params = Map.<String, Object>of("patient", patientId, "facility", facilityId);
        return jdbc.query("""
            SELECT e.id, e.created_at, e.state, d.name AS department, u.username AS clinician
            FROM encounter e
            JOIN patient_canonical_identity ci ON ci.alias_id=e.patient_id
            JOIN department d ON d.id=e.department_id
            LEFT JOIN app_user u ON u.id=e.assigned_provider_id
            WHERE ci.patient_id=(SELECT patient_id FROM patient_canonical_identity WHERE alias_id=:patient)
              AND e.facility_id=:facility AND e.state <> 'CANCELLED_IN_ERROR'
            ORDER BY e.created_at DESC, e.id DESC
            """, params, (rs, n) -> {
            UUID encounterId = rs.getObject("id", UUID.class);
            return new Visit(encounterId, rs.getTimestamp("created_at").toInstant(),
                    rs.getString("department"), rs.getString("clinician"), rs.getString("state"),
                    invoices(encounterId));
        });
    }

    private List<VisitInvoice> invoices(UUID encounterId) {
        return jdbc.query("""
            SELECT i.id, i.invoice_number, i.state, i.payer_category, i.currency_code,
                   i.gross_total_minor, i.patient_portion_total_minor, i.insurance_portion_total_minor,
                   i.outstanding_total_minor, coalesce(paid.amount,0) AS paid,
                   coalesce(claim.approved,0) AS insurance_approved
            FROM invoice i
            LEFT JOIN LATERAL (
              SELECT sum(pa.amount_minor-pa.refunded_minor) AS amount
              FROM invoice_line il JOIN payment_allocation pa ON pa.invoice_line_id=il.id
              JOIN payment p ON p.id=pa.payment_id AND p.status='SETTLED'
              WHERE il.invoice_id=i.id
            ) paid ON true
            LEFT JOIN LATERAL (
              SELECT sum(approved_minor) AS approved FROM insurance_claim
              WHERE invoice_id=i.id AND state IN ('APPROVED','SETTLED')
            ) claim ON true
            WHERE i.encounter_id=:encounter ORDER BY i.created_at, i.id
            """, Map.of("encounter", encounterId), (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            String state = rs.getString("state");
            long paid = rs.getLong("paid");
            long patient = rs.getLong("patient_portion_total_minor");
            long insurance = rs.getLong("insurance_portion_total_minor");
            boolean inactive = "VOID".equals(state) || "DRAFT".equals(state);
            long outstanding = rs.getLong("outstanding_total_minor");
            long insuranceDue = inactive ? 0 : Math.min(outstanding,
                    Math.max(0, insurance - rs.getLong("insurance_approved")));
            long patientDue = inactive ? 0 : Math.max(0,
                    Math.min(patient - paid, outstanding - insuranceDue));
            return new VisitInvoice(id, rs.getString("invoice_number"), state,
                    rs.getString("payer_category"), rs.getString("currency_code"),
                    rs.getLong("gross_total_minor"), patient, insurance, paid,
                    patientDue, insuranceDue,
                    payments(id));
        });
    }

    private List<VisitPayment> payments(UUID invoiceId) {
        return jdbc.query("""
            SELECT p.id, sum(pa.amount_minor-pa.refunded_minor) AS amount, p.method, p.status,
                   p.received_at, p.reference
            FROM invoice_line il JOIN payment_allocation pa ON pa.invoice_line_id=il.id
            JOIN payment p ON p.id=pa.payment_id
            WHERE il.invoice_id=:invoice
            GROUP BY p.id, p.method, p.status, p.received_at, p.reference
            ORDER BY p.received_at DESC
            """, Map.of("invoice", invoiceId), (rs, n) -> new VisitPayment(
                rs.getObject("id", UUID.class), rs.getLong("amount"), rs.getString("method"),
                rs.getString("status"), rs.getTimestamp("received_at").toInstant(),
                rs.getString("reference")));
    }
}
