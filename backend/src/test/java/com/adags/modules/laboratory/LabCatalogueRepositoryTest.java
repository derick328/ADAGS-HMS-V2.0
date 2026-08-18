package com.adags.modules.laboratory;

import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.Sex;
import com.adags.modules.laboratory.domain.VerificationLevel;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 15-lab-technician.md §5.1: catalogue tables the phase-5 result form
// generates from. No migration seeds any of these — "seed nothing you
// cannot cite" — so every row here is a test fixture, left in place
// afterwards (harmless accumulation, same as PriceListEntryRepositoryTest's
// service_item fixtures).
class LabCatalogueRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private LabTestRepository labTestRepository;
    @Autowired
    private LabTestParameterRepository labTestParameterRepository;
    @Autowired
    private LabReferenceRangeRepository labReferenceRangeRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void aLabTestRoundTripsByCode() {
        String code = "FBC-" + UUID.randomUUID();
        LabTest saved = labTestRepository.insert(LabTest.create(
                code, "Full Blood Count", "BLOOD", "EDTA_TUBE", 24, VerificationLevel.NONE, true));

        LabTest found = labTestRepository.findByCode(code).orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.verificationLevel()).isEqualTo(VerificationLevel.NONE);
        assertThat(found.isActive()).isTrue();
    }

    // §5.1: "The result form is generated from LabTestParameter for that
    // test" — findByTestId must return parameters in the catalogue's
    // declared sort order, not insertion order.
    @Test
    void parametersAreReturnedInSortOrderNotInsertionOrder() {
        LabTest test = labTestRepository.insert(LabTest.create(
                "FBC-" + UUID.randomUUID(), "Full Blood Count", "BLOOD", "EDTA_TUBE", 24, VerificationLevel.NONE, true));

        labTestParameterRepository.insert(LabTestParameter.create(test.id(), "WBC", "White Cell Count", "x10^9/L", 2, null, null));
        labTestParameterRepository.insert(LabTestParameter.create(test.id(), "HGB", "Haemoglobin", "g/dL", 1, null, "718-7"));

        List<LabTestParameter> parameters = labTestParameterRepository.findByTestId(test.id());

        assertThat(parameters).extracting(LabTestParameter::code).containsExactly("HGB", "WBC");
    }

    @Test
    void aReferenceRangeRoundTripsAsTheCurrentRowForItsParameter() {
        LabTest test = labTestRepository.insert(LabTest.create(
                "FBC-" + UUID.randomUUID(), "Full Blood Count", "BLOOD", "EDTA_TUBE", 24, VerificationLevel.NONE, true));
        LabTestParameter parameter = labTestParameterRepository.insert(
                LabTestParameter.create(test.id(), "HGB", "Haemoglobin", "g/dL", 1, null, "718-7"));
        UUID admin = bootstrapAdminId();

        labReferenceRangeRepository.insert(LabReferenceRange.create(
                parameter.id(), 216, null, Sex.MALE, 3.0, 25.0, 13.0, 17.0, 7.0, 20.0, admin));

        List<LabReferenceRange> current = labReferenceRangeRepository.findCurrentByParameterId(parameter.id());
        assertThat(current).hasSize(1);
        assertThat(current.get(0).normalLow()).isEqualTo(13.0);
        assertThat(current.get(0).validTo()).isNull();
    }

    // Same current-row guard as price_list_entry / vital_reference_range —
    // the partial unique index, not application code, is what "never
    // edited in place" actually rests on.
    @Test
    void aSecondCurrentRangeForTheSameParameterAgeBandAndSexIsRejected() {
        LabTest test = labTestRepository.insert(LabTest.create(
                "FBC-" + UUID.randomUUID(), "Full Blood Count", "BLOOD", "EDTA_TUBE", 24, VerificationLevel.NONE, true));
        LabTestParameter parameter = labTestParameterRepository.insert(
                LabTestParameter.create(test.id(), "HGB", "Haemoglobin", "g/dL", 1, null, "718-7"));
        UUID admin = bootstrapAdminId();
        labReferenceRangeRepository.insert(LabReferenceRange.create(
                parameter.id(), 216, null, Sex.MALE, 3.0, 25.0, 13.0, 17.0, 7.0, 20.0, admin));

        assertThatThrownBy(() -> labReferenceRangeRepository.insert(LabReferenceRange.create(
                parameter.id(), 216, null, Sex.MALE, 3.0, 25.0, 13.5, 17.5, 7.0, 20.0, admin)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
