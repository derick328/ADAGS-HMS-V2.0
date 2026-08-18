package com.adags.modules.laboratory.domain;

// 15-lab-technician.md §4.2: "quality_note Required when quality != ADEQUATE."
public class MissingQualityNoteException extends RuntimeException {

    public MissingQualityNoteException(SpecimenQuality quality) {
        super("A quality note is required when specimen quality is " + quality + " (not ADEQUATE).");
    }
}
