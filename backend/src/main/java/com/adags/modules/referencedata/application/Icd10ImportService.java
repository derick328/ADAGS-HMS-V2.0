package com.adags.modules.referencedata.application;

import com.adags.modules.referencedata.domain.Icd10Code;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 10-clinical-reference-data.md §4.1: "Import the full WHO ICD-10 release
// with an empty common list." This is the import path the spec asks for —
// deliberately NOT wired to any actual WHO data (§4.1: "seeding is an
// operational task for the clinical lead"). One release per call,
// transactional as a whole: icd10_code.parent_code's FK is DEFERRABLE
// INITIALLY DEFERRED (V13 migration) specifically so a batch containing a
// forward reference to a not-yet-inserted parent still succeeds, but only
// if every row lands in the same transaction — a half-imported release
// left by a partial failure would be worse than no import at all.
@Service
public class Icd10ImportService {

    private final Icd10CodeRepository repository;

    public Icd10ImportService(Icd10CodeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Icd10ImportResult importRelease(String icd10Release, List<Icd10ImportRow> rows) {
        if (icd10Release == null || icd10Release.isBlank()) {
            throw new IllegalArgumentException("icd10Release is required.");
        }
        int inserted = 0;
        int updated = 0;
        for (Icd10ImportRow row : rows) {
            boolean wasInserted = repository.upsert(new Icd10Code(
                    row.code(), row.title(), row.chapter(), row.block(), row.parentCode(),
                    icd10Release, row.isBillable(), true, null));
            if (wasInserted) {
                inserted++;
            } else {
                updated++;
            }
        }
        return new Icd10ImportResult(inserted, updated);
    }
}
