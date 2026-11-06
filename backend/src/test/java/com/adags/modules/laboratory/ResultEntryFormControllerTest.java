package com.adags.modules.laboratory;

import com.adags.modules.laboratory.api.ResultEntryFormController;
import com.adags.modules.laboratory.domain.*;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.PatientDemographics;
import com.adags.shared.kernel.PatientIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResultEntryFormControllerTest {
    private final LabOrderRepository orders = mock(LabOrderRepository.class);
    private final LabTestRepository tests = mock(LabTestRepository.class);
    private final LabTestParameterRepository parameters = mock(LabTestParameterRepository.class);
    private final LabReferenceRangeRepository ranges = mock(LabReferenceRangeRepository.class);
    private final SpecimenRepository specimens = mock(SpecimenRepository.class);
    private final LabResultRepository results = mock(LabResultRepository.class);
    private final PatientFacade patients = mock(PatientFacade.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ResultEntryFormController controller = new ResultEntryFormController(
            orders, tests, parameters, ranges, specimens, results, patients, authorization);

    @Test
    void requestInformationUsesOrderSpecimenAndPatientAndHidesNationalIdWithoutPermission() {
        UUID orderId = UUID.randomUUID(), patientId = UUID.randomUUID(), facilityId = UUID.randomUUID();
        UUID testId = UUID.randomUUID(), specimenId = UUID.randomUUID();
        Actor actor = new Actor(UUID.randomUUID());
        LabOrder order = new LabOrder(orderId, UUID.randomUUID(), patientId, facilityId, testId,
                UUID.randomUUID(), Instant.now(), "Anaemia", null, Urgency.URGENT, UUID.randomUUID(), Instant.now());
        LabTest test = LabTest.create("FBC", "Full blood count", "BLOOD", "EDTA_TUBE", 4,
                VerificationLevel.NONE, true);
        test = new LabTest(testId, test.code(), test.name(), test.specimenType(), test.containerType(),
                test.expectedTatHours(), test.verificationLevel(), test.producesMachineOutput(), true, Instant.now());
        Specimen specimen = new Specimen(specimenId, orderId, "ACC-1", "BLOOD", "EDTA_TUBE",
                actor.userId(), Instant.now(), actor.userId(), Instant.now(), SpecimenQuality.ADEQUATE,
                null, SpecimenStatus.IN_PROGRESS, null, Instant.now());
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(tests.findById(testId)).thenReturn(Optional.of(test));
        when(specimens.findByLabOrderId(orderId)).thenReturn(List.of(specimen));
        when(results.findByLabOrderId(orderId)).thenReturn(List.of());
        when(parameters.findByTestId(testId)).thenReturn(List.of());
        when(patients.findIdentity(patientId)).thenReturn(Optional.of(new PatientIdentity("H-1", "Asha", null, "Juma")));
        when(patients.findDemographics(patientId)).thenReturn(Optional.of(new PatientDemographics(LocalDate.of(1990, 1, 1), "FEMALE")));
        when(patients.findNationalId(patientId)).thenReturn(Optional.of("SECRET-ID"));
        ScopeRef scope = ScopeRef.of(ScopeType.FACILITY, facilityId);
        when(authorization.hasPermission(actor, "LAB_RESULT_ENTER", scope)).thenReturn(true);

        var hidden = controller.resultEntryForm(orderId, actor);
        assertThat(hidden.patientName()).isEqualTo("Asha Juma");
        assertThat(hidden.hospitalNumber()).isEqualTo("H-1");
        assertThat(hidden.labTestCode()).isEqualTo("FBC");
        assertThat(hidden.urgency()).isEqualTo("URGENT");
        assertThat(hidden.accessionNo()).isEqualTo("ACC-1");
        assertThat(hidden.specimenQuality()).isEqualTo(SpecimenQuality.ADEQUATE);
        assertThat(hidden.specialInstructions()).isNull();
        assertThat(hidden.nationalId()).isNull();
        verify(patients, never()).findNationalId(patientId);

        when(authorization.hasPermission(actor, "PATIENT_READ_DEMOGRAPHIC", scope)).thenReturn(true);
        assertThat(controller.resultEntryForm(orderId, actor).nationalId()).isEqualTo("SECRET-ID");
    }
}
