package com.adags.shared.kernel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DosageFormTest {
    @Test
    void recognizesEveryFormWithoutStrippingMeaningfulEndings() {
        assertEquals(167, DosageForm.values().length);
        for (DosageForm form : DosageForm.values()) {
            assertEquals(form, DosageForm.fromCatalogue(form.name()));
            assertEquals(form, DosageForm.fromCatalogue(form.name().toLowerCase().replace('_', ' ')));
        }
        assertEquals(DosageForm.ORALLY_DISINTEGRATING_TABLET_ODT,
                DosageForm.fromCatalogue("Orally Disintegrating Tablet (ODT)"));
        assertEquals(DosageForm.FILM_COATED_TABLET, DosageForm.fromCatalogue("Film-Coated Tablet"));
    }

    @Test
    void canonicalizesLegacyTabletsAndPreservesQuantityPolicy() {
        for (String alias : new String[] {"Tablet", "Tablets", "Oral Tablets"}) {
            DosageForm form = DosageForm.requireSupported(alias);
            assertEquals(DosageForm.TABLET, form);
            assertEquals("TABLET", form.baseUnit());
            assertTrue(form.automaticQuantity());
        }
        assertEquals(DosageForm.CAPSULE, DosageForm.fromCatalogue("Capsules"));
        assertFalse(DosageForm.INHALATION_GAS.automaticQuantity());
        assertNull(DosageForm.fromCatalogue("Unknown form"));
    }
}
