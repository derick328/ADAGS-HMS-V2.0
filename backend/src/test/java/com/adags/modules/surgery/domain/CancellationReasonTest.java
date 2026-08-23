package com.adags.modules.surgery.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationReasonTest {

    @Test
    void classifiesPatientSideReasons() {
        assertThat(CancellationReason.PATIENT_REQUEST.isPatientSide()).isTrue();
        assertThat(CancellationReason.PATIENT_UNFIT.isPatientSide()).isTrue();
        assertThat(CancellationReason.NO_SHOW.isPatientSide()).isTrue();
        assertThat(CancellationReason.PATIENT_REQUEST.isHospitalSide()).isFalse();
        assertThat(CancellationReason.PATIENT_REQUEST.isClinicalDecision()).isFalse();
    }

    @Test
    void classifiesHospitalSideReasons() {
        assertThat(CancellationReason.SURGEON_UNAVAILABLE.isHospitalSide()).isTrue();
        assertThat(CancellationReason.THEATRE_UNAVAILABLE.isHospitalSide()).isTrue();
        assertThat(CancellationReason.EQUIPMENT_FAILURE.isHospitalSide()).isTrue();
        assertThat(CancellationReason.STAFF_UNAVAILABLE.isHospitalSide()).isTrue();
        assertThat(CancellationReason.SURGEON_UNAVAILABLE.isPatientSide()).isFalse();
    }

    @Test
    void clinicalDecisionIsItsOwnBucket() {
        assertThat(CancellationReason.CLINICAL_DECISION.isClinicalDecision()).isTrue();
        assertThat(CancellationReason.CLINICAL_DECISION.isPatientSide()).isFalse();
        assertThat(CancellationReason.CLINICAL_DECISION.isHospitalSide()).isFalse();
    }
}
