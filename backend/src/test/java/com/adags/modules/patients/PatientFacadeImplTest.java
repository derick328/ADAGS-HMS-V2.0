package com.adags.modules.patients;

import com.adags.shared.kernel.PayerType;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Phase 7 item 3 part 2: resolveActiveInsuranceSchemeId is the seam
// ChargePostingService uses to find which scheme's coverage rules apply
// — same "active" definition as resolvePayerType (InsurancePolicy
// .isValidOn), deliberately not checking verification_status (that's the
// billing gate's concern, a later part).
class PatientFacadeImplTest extends IntegrationTestSupport {

    @Autowired
    private PatientFacade patientFacade;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE insurance_policy, insurance_scheme, patient CASCADE", new MapSqlParameterSource());
    }

    @Test
    void resolvesTheSchemeOfAnActivePolicy() {
        UUID patientId = insertFixturePatient();
        UUID schemeId = insertFixtureScheme();
        insertFixturePolicy(patientId, schemeId, LocalDate.now().minusDays(10), null);

        assertThat(patientFacade.resolveActiveInsuranceSchemeId(patientId, LocalDate.now())).contains(schemeId);
        assertThat(patientFacade.resolvePayerType(patientId, LocalDate.now())).isEqualTo(PayerType.INSURANCE);
    }

    @Test
    void isEmptyWhenThePolicyHasExpired() {
        UUID patientId = insertFixturePatient();
        UUID schemeId = insertFixtureScheme();
        insertFixturePolicy(patientId, schemeId, LocalDate.now().minusDays(400), LocalDate.now().minusDays(30));

        assertThat(patientFacade.resolveActiveInsuranceSchemeId(patientId, LocalDate.now())).isEmpty();
    }

    @Test
    void isEmptyWithNoPolicyAtAll() {
        UUID patientId = insertFixturePatient();

        assertThat(patientFacade.resolveActiveInsuranceSchemeId(patientId, LocalDate.now())).isEmpty();
    }

    private UUID insertFixturePatient() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """,
                new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertFixtureScheme() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO insurance_scheme (code, name) VALUES (:code, 'Test Scheme') RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-SCHEME-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private void insertFixturePolicy(UUID patientId, UUID schemeId, LocalDate validFrom, LocalDate validTo) {
        jdbcTemplate.update("""
                        INSERT INTO insurance_policy (patient_id, scheme_id, policy_number, valid_from, valid_to)
                        VALUES (:patientId, :schemeId, :policyNumber, :validFrom, :validTo)
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("schemeId", schemeId)
                        .addValue("policyNumber", "POL-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("validFrom", validFrom)
                        .addValue("validTo", validTo));
    }
}
