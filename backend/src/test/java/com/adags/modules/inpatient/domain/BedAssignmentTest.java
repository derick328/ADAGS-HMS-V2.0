package com.adags.modules.inpatient.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 17-ward-nurse.md §1 ⚠2's resolution table. Mirrors the DB CHECK
// (bed_assignment_placement_reason_required) at the domain layer — see
// BedAssignmentConcurrencyTest for proof the database itself rejects the
// bad case independent of this validation.
class BedAssignmentTest {

    private final UUID admissionId = UUID.randomUUID();
    private final UUID bedId = UUID.randomUUID();
    private final UUID assignedBy = UUID.randomUUID();

    @Test
    void sameClassPlacementRequiresNoReasonAndIsNotAboveOrderedClass() {
        BedAssignment assignment = BedAssignment.create(
                admissionId, bedId, WardClass.GENERAL, WardClass.GENERAL, null, assignedBy);

        assertThat(assignment.placedAboveOrderedClass()).isFalse();
        assertThat(assignment.placementReason()).isNull();
    }

    @Test
    void placingAboveOrderedClassWithAReasonIsRecorded() {
        BedAssignment assignment = BedAssignment.create(
                admissionId, bedId, WardClass.PRIVATE, WardClass.GENERAL, PlacementReason.NO_BED_IN_ORDERED_CLASS, assignedBy);

        assertThat(assignment.placedAboveOrderedClass()).isTrue();
        assertThat(assignment.placementReason()).isEqualTo(PlacementReason.NO_BED_IN_ORDERED_CLASS);
    }

    @Test
    void placingAboveOrderedClassWithNoReasonIsRejected() {
        assertThatThrownBy(() -> BedAssignment.create(
                admissionId, bedId, WardClass.PRIVATE, WardClass.GENERAL, null, assignedBy))
                .isInstanceOf(MissingPlacementReasonException.class);
    }

    @Test
    void aReasonOnASameClassPlacementIsRejected() {
        assertThatThrownBy(() -> BedAssignment.create(
                admissionId, bedId, WardClass.GENERAL, WardClass.GENERAL, PlacementReason.OTHER, assignedBy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privateIsAboveGeneralButNotViceVersa() {
        assertThat(WardClass.PRIVATE.isAbove(WardClass.GENERAL)).isTrue();
        assertThat(WardClass.GENERAL.isAbove(WardClass.PRIVATE)).isFalse();
        assertThat(WardClass.GENERAL.isAbove(WardClass.GENERAL)).isFalse();
    }
}
