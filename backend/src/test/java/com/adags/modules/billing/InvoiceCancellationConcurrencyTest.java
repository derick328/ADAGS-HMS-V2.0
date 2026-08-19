package com.adags.modules.billing;

import com.adags.modules.billing.application.RequestInvoiceCancellationCommand;
import com.adags.modules.billing.application.RequestInvoiceCancellationService;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceAlreadyHasPendingCancellationException;
import com.adags.modules.billing.domain.InvoiceCancellationRequest;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// root CLAUDE.md "Concurrency": 18-admin.md §6 — "One PENDING request per
// invoice at a time" — is a partial unique index
// (idx_invoice_cancellation_request_one_pending), not a read-then-write
// check. Fires real concurrent requests against the same invoice and
// proves the database rejects all but one.
class InvoiceCancellationConcurrencyTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private RequestInvoiceCancellationService requestInvoiceCancellationService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceLineRepository invoiceLineRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE invoice_cancellation_request, service_authorization, invoice_line, invoice,
                                 encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    @Test
    void concurrentRequestsAgainstTheSameInvoiceLeaveExactlyOnePending() throws Exception {
        UUID admin = bootstrapAdminId();
        Invoice issued = insertFixtureIssuedInvoice(15_000, admin);

        int racers = 5;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<InvoiceCancellationRequest>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < racers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return requestInvoiceCancellationService.request(
                                new RequestInvoiceCancellationCommand(issued.id(), "racing request", admin, null));
                    } catch (InvoiceAlreadyHasPendingCancellationException ex) {
                        return null;
                    }
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long successes = futures.stream().map(this::resolve).filter(r -> r != null).count();
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice_cancellation_request WHERE invoice_id = :id AND status = 'PENDING'",
                new MapSqlParameterSource("id", issued.id()), Integer.class);
        assertThat(pendingCount).isEqualTo(1);
    }

    private InvoiceCancellationRequest resolve(Future<InvoiceCancellationRequest> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Invoice insertFixtureIssuedInvoice(long grossMinor, UUID admin) {
        UUID patientId = insertFixturePatient();
        UUID encounterId = insertFixtureEncounter(patientId);
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, BOOTSTRAP_FACILITY_ID,
                PayerCategory.CASH, null, admin));
        invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(grossMinor, "TZS"), new Money(0, "TZS"), new Money(grossMinor, "TZS"),
                "TriageCompleted", encounterId));
        return invoiceRepository.issue(draft.id(), "FAC-2026-" + UUID.randomUUID().toString().substring(0, 6), draft.version());
    }

    private UUID insertFixtureServiceItem() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO service_item (code, name, category)
                        VALUES (:code, 'Test Item', 'CONSULTATION')
                        RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-ITEM-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertFixtureEncounter(UUID patientId) {
        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, code, name)
                        VALUES (:facilityId, :code, 'Test Department')
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);

        return jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'AWAITING_PAYMENT', 'CASH',
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", BOOTSTRAP_FACILITY_ID)
                        .addValue("departmentId", departmentId),
                UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
