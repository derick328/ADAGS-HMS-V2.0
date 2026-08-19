package com.adags.modules.insurance;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.insurance.application.TransitionInsuranceClaimCommand;
import com.adags.modules.insurance.application.TransitionInsuranceClaimService;
import com.adags.modules.insurance.domain.ClaimState;
import com.adags.modules.insurance.domain.InsuranceClaim;
import com.adags.modules.insurance.domain.InsuranceClaimRepository;
import com.adags.modules.insurance.domain.InsuranceScheme;
import com.adags.modules.insurance.domain.InsuranceSchemeRepository;
import com.adags.modules.insurance.domain.InvalidClaimTransitionException;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitionInsuranceClaimServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private TransitionInsuranceClaimService transitionInsuranceClaimService;
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
    void walksTheFullRejectAndResubmitGraphToApproval() {
        InsuranceClaim claim = insertFixtureClaim();

        InsuranceClaim submitted = transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claim.id(), ClaimState.SUBMITTED, null));
        assertThat(submitted.state()).isEqualTo(ClaimState.SUBMITTED);

        InsuranceClaim rejected = transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claim.id(), ClaimState.REJECTED, "missing pre-authorization"));
        assertThat(rejected.state()).isEqualTo(ClaimState.REJECTED);
        assertThat(rejected.rejectedReason()).isEqualTo("missing pre-authorization");

        InsuranceClaim resubmitted = transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claim.id(), ClaimState.RESUBMITTED, null));
        assertThat(resubmitted.state()).isEqualTo(ClaimState.RESUBMITTED);

        InsuranceClaim approved = transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claim.id(), ClaimState.APPROVED, null));
        assertThat(approved.state()).isEqualTo(ClaimState.APPROVED);
    }

    @Test
    void skippingAStateInTheGraphIsRejected() {
        InsuranceClaim claim = insertFixtureClaim();

        assertThatThrownBy(() -> transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claim.id(), ClaimState.APPROVED, null)))
                .isInstanceOf(InvalidClaimTransitionException.class);
    }

    @Test
    void rejectingWithoutAReasonIsRejected() {
        InsuranceClaim claim = insertFixtureClaim();
        transitionInsuranceClaimService.transition(new TransitionInsuranceClaimCommand(claim.id(), ClaimState.SUBMITTED, null));

        assertThatThrownBy(() -> transitionInsuranceClaimService.transition(
                new TransitionInsuranceClaimCommand(claim.id(), ClaimState.REJECTED, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private InsuranceClaim insertFixtureClaim() {
        UUID admin = bootstrapAdminId();
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
        Invoice invoice = invoiceRepository.insertDraft(
                Invoice.draft(null, patientId, BOOTSTRAP_FACILITY_ID, PayerCategory.NHIF, null, admin));
        InsuranceScheme scheme = schemeRepository.insert(InsuranceScheme.create(
                "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8), "Test Scheme", null, null, null));
        return claimRepository.insert(InsuranceClaim.raise(
                invoice.id(), scheme.id(), "FAC-2026-" + UUID.randomUUID().toString().substring(0, 6)));
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
