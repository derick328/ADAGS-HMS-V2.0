package com.adags.modules.pharmacy.application;

import com.adags.modules.pharmacy.domain.PrescriptionFrequency;
import com.adags.shared.kernel.ItemSummary;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrescriptionQuantityPolicyTest {
    private final UUID itemId = UUID.randomUUID();

    private ItemSummary item(String form, String baseUnit) {
        return new ItemSummary(itemId, "MED-1", "Test medicine", baseUnit, "PHARMACY",
                form, true, true, false);
    }

    private PlacePrescriptionCommand prescription(int baseQuantity, double dose, String doseUnit,
                                                   PrescriptionFrequency frequency, Integer days,
                                                   int supplyQuantity, String supplyUnit) {
        return new PlacePrescriptionCommand(UUID.randomUUID(), itemId, baseQuantity, "As directed",
                false, UUID.randomUUID(), "ORAL", dose, doseUnit, frequency, days,
                supplyQuantity, supplyUnit);
    }

    @Test
    void rejectsForgedSolidSupplyAndFractionalUnits() {
        var capsule = item("Capsule", "CAPSULE");
        var correct = prescription(12, 2, "CAPSULE", PrescriptionFrequency.BD, 3, 12, "CAPSULE");
        assertThatCode(() -> PlacePrescriptionService.validateQuantityPolicy(capsule, correct, 12, "CAPSULE"))
                .doesNotThrowAnyException();
        var forged = prescription(30, 2, "CAPSULE", PrescriptionFrequency.BD, 3, 30, "CAPSULE");
        assertThatThrownBy(() -> PlacePrescriptionService.validateQuantityPolicy(capsule, forged, 30, "CAPSULE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Dispensing quantity");
        var fractional = prescription(3, 0.5, "CAPSULE", PrescriptionFrequency.BD, 3, 3, "CAPSULE");
        assertThatThrownBy(() -> PlacePrescriptionService.validateQuantityPolicy(capsule, fractional, 3, "CAPSULE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("whole unit");
        var wrongUnit = prescription(12, 2, "mg", PrescriptionFrequency.BD, 3, 12, "CAPSULE");
        assertThatThrownBy(() -> PlacePrescriptionService.validateQuantityPolicy(capsule, wrongUnit, 12, "CAPSULE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("base unit");
    }

    @Test
    void statIsOneDoseAndPrnUsesExplicitSupply() {
        var tablet = item("TABLET", "TABLET");
        var stat = prescription(2, 2, "TABLET", PrescriptionFrequency.STAT, null, 2, "TABLET");
        assertThatCode(() -> PlacePrescriptionService.validateQuantityPolicy(tablet, stat, 2, "TABLET"))
                .doesNotThrowAnyException();
        var prn = prescription(14, 1, "TABLET", PrescriptionFrequency.PRN, null, 14, "TABLET");
        assertThatCode(() -> PlacePrescriptionService.validateQuantityPolicy(tablet, prn, 14, "TABLET"))
                .doesNotThrowAnyException();
        var weekly = new PlacePrescriptionCommand(UUID.randomUUID(), itemId, 4,
                "Take one tablet every Monday for four weeks", false, UUID.randomUUID(),
                "ORAL", 1.0, "TABLET", PrescriptionFrequency.OTHER, null, 4, "TABLET");
        assertThatCode(() -> PlacePrescriptionService.validateQuantityPolicy(tablet, weekly, 4, "TABLET"))
                .doesNotThrowAnyException();
    }

    @Test
    void syrupSupplyIsIndependentOfDoseScheduleAndRequiresPackageUnit() {
        var syrup = item("Oral Suspension", "mL");
        var bottle = prescription(100, 5, "mL", PrescriptionFrequency.BD, 3, 1, "BOTTLE_100");
        assertThatCode(() -> PlacePrescriptionService.validateQuantityPolicy(syrup, bottle, 1, "BOTTLE_100"))
                .doesNotThrowAnyException();
        var volume = prescription(30, 5, "mL", PrescriptionFrequency.BD, 3, 30, "mL");
        assertThatThrownBy(() -> PlacePrescriptionService.validateQuantityPolicy(syrup, volume, 30, "mL"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("approved supply package");
    }

    @Test
    void injectionKeepsDoseSeparateFromVialCount() {
        var injection = item("INJECTION", "VIAL");
        var fourVials = prescription(4, 0.5, "mg", PrescriptionFrequency.STAT, null, 4, "VIAL");
        assertThatCode(() -> PlacePrescriptionService.validateQuantityPolicy(injection, fourVials, 4, "VIAL"))
                .doesNotThrowAnyException();
    }

    @Test
    void unconfiguredMedicineCannotBypassTheDosageFormPolicy() {
        var unknown = item("—", "unit");
        var command = prescription(5, 1, "unit", PrescriptionFrequency.OD, 5, 5, "unit");
        assertThatThrownBy(() -> PlacePrescriptionService.validateQuantityPolicy(unknown, command, 5, "unit"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Assign a supported dosage form");
    }
}
