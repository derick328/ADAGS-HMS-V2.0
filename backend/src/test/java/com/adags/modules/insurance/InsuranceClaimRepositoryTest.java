package com.adags.modules.insurance;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.InsuranceClaim;
import com.adags.modules.insurance.domain.InsuranceClaimRepository;
import com.adags.modules.insurance.domain.InsuranceClaimStateConflictException;
import com.adags.modules.insurance.domain.InsuranceScheme;
import com.adags.modules.insurance.domain.InsuranceSchemeRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §11's InsuranceClaim. Phase 7 item 3 part 3:
// unlike Invoice's two-step draft()/issue(), a claim's number is
// allocated at the same moment it's raised (still DRAFT) — there is no
// manual entry to protect against here.
class InsuranceClaimRepositoryTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private InsuranceClaimRepository claimRepository;
    @Autowired
    private InsuranceSchemeRepository schemeRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "TRUNCATE insurance_claim, insurance_scheme, invoice, encounter, patient CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void insertingARaisedClaimStartsInDraftWithItsNumberAlreadyAllocated() {
        UUID admin = bootstrapAdminId();
        UUID invoiceId = insertFixtureInvoice(admin);
        UUID schemeId = insertFixtureScheme();

        InsuranceClaim raised = claimRepository.insert(InsuranceClaim.raise(invoiceId, schemeId, "FAC-2026-000001"));

        assertThat(raised.id()).isNotNull();
        assertThat(raised.state()).isEqualTo(ClaimState.DRAFT);
        assertThat(raised.claimNo()).isEqualTo("FAC-2026-000001");

        InsuranceClaim found = claimRepository.findById(raised.id()).orElseThrow();
        assertThat(found.invoiceId()).isEqualTo(invoiceId);
        assertThat(found.schemeId()).isEqualTo(schemeId);
    }

    @Test
    void raiseRejectsABlankClaimNo() {
        assertThatThrownBy(() -> InsuranceClaim.raise(UUID.randomUUID(), UUID.randomUUID(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transitionMovesTheClaimAndStampsSubmittedAt() {
        UUID admin = bootstrapAdminId();
        UUID invoiceId = insertFixtureInvoice(admin);
        UUID schemeId = insertFixtureScheme();
        InsuranceClaim raised = claimRepository.insert(InsuranceClaim.raise(invoiceId, schemeId, "FAC-2026-000002"));

        InsuranceClaim submitted = claimRepository.transition(raised.id(), ClaimState.DRAFT, ClaimState.SUBMITTED, null);

        assertThat(submitted.state()).isEqualTo(ClaimState.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();
    }

    @Test
    void transitionToRejectedStoresTheReason() {
        UUID admin = bootstrapAdminId();
        UUID invoiceId = insertFixtureInvoice(admin);
        UUID schemeId = insertFixtureScheme();
        InsuranceClaim raised = claimRepository.insert(InsuranceClaim.raise(invoiceId, schemeId, "FAC-2026-000003"));
        claimRepository.transition(raised.id(), ClaimState.DRAFT, ClaimState.SUBMITTED, null);

        InsuranceClaim rejected = claimRepository.transition(
                raised.id(), ClaimState.SUBMITTED, ClaimState.REJECTED, "missing pre-authorization");

        assertThat(rejected.state()).isEqualTo(ClaimState.REJECTED);
        assertThat(rejected.rejectedReason()).isEqualTo("missing pre-authorization");
    }

    // root CLAUDE.md "Concurrency": the WHERE-guarded UPDATE, not a prior
    // read, is what stops two decisions racing against the same claim.
    @Test
    void transitionFromTheWrongExpectedStateIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID invoiceId = insertFixtureInvoice(admin);
        UUID schemeId = insertFixtureScheme();
        InsuranceClaim raised = claimRepository.insert(InsuranceClaim.raise(invoiceId, schemeId, "FAC-2026-000004"));

        assertThatThrownBy(() -> claimRepository.transition(raised.id(), ClaimState.SUBMITTED, ClaimState.APPROVED, null))
                .isInstanceOf(InsuranceClaimStateConflictException.class);
    }

    // The DB CHECK behind the same two-step pattern invoice_number uses
    // (V16): a number exists for every state except DRAFT.
    @Test
    void theDatabaseRejectsANonDraftClaimWithNoClaimNumber() {
        UUID admin = bootstrapAdminId();
        UUID invoiceId = insertFixtureInvoice(admin);
        UUID schemeId = insertFixtureScheme();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO insurance_claim (invoice_id, scheme_id, state)
                        VALUES (:invoiceId, :schemeId, 'SUBMITTED')
                        """,
                new MapSqlParameterSource().addValue("invoiceId", invoiceId).addValue("schemeId", schemeId)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theDatabaseRejectsARejectedClaimWithNoReason() {
        UUID admin = bootstrapAdminId();
        UUID invoiceId = insertFixtureInvoice(admin);
        UUID schemeId = insertFixtureScheme();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO insurance_claim (invoice_id, scheme_id, state, claim_no)
                        VALUES (:invoiceId, :schemeId, 'REJECTED', 'CLAIM-0001')
                        """,
                new MapSqlParameterSource().addValue("invoiceId", invoiceId).addValue("schemeId", schemeId)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertFixtureInvoice(UUID admin) {
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
        Invoice draft = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null, admin));
        return draft.id();
    }

    private UUID insertFixtureScheme() {
        InsuranceScheme scheme = schemeRepository.insert(InsuranceScheme.create(
                "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8), "Test Scheme", null, null, null));
        return scheme.id();
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
