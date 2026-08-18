package com.adags.modules.billing;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceNotEditableException;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §6.3/§10: "Lines locked" once ISSUED.
class InvoiceLineRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE service_authorization, invoice_line, invoice, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertingALineOnADraftInvoiceSucceeds() {
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = insertDraftInvoice();

        InvoiceLine saved = invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(10_000, "TZS"), new Money(0, "TZS"), new Money(10_000, "TZS"),
                "TriageCompleted", UUID.randomUUID()));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.authorizationId()).isNull();
        assertThat(saved.coverageRuleId()).isNull();

        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceId(draft.id());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).grossAmount()).isEqualTo(new Money(10_000, "TZS"));
    }

    @Test
    void insertingALineOnAnIssuedInvoiceIsRejected() {
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = insertDraftInvoice();
        Invoice issued = invoiceRepository.issue(draft.id(), "FAC-2026-100001", draft.version());

        assertThatThrownBy(() -> invoiceLineRepository.insert(InvoiceLine.unauthorized(issued.id(), serviceItemId, 1,
                new Money(10_000, "TZS"), new Money(0, "TZS"), new Money(10_000, "TZS"),
                "TriageCompleted", UUID.randomUUID())))
                .isInstanceOf(InvoiceNotEditableException.class);
    }

    // Real Postgres CHECK constraint, not just an application assumption.
    @Test
    void grossMustEqualInsurancePlusPatientPortion() {
        UUID serviceItemId = insertFixtureServiceItem();
        Invoice draft = insertDraftInvoice();

        assertThatThrownBy(() -> invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItemId, 1,
                new Money(10_000, "TZS"), new Money(0, "TZS"), new Money(9_000, "TZS"),
                "TriageCompleted", UUID.randomUUID())))
                .isInstanceOf(DataAccessException.class);
    }

    private Invoice insertDraftInvoice() {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
        UUID adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);

        return invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.CASH, null, adminId));
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
}
