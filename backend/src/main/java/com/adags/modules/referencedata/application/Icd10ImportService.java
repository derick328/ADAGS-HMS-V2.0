package com.adags.modules.referencedata.application;

import com.adags.modules.referencedata.domain.Icd10Code;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

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

    public static final String WHO_2019_RELEASE = "WHO-ICD-10-2019";

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

    /** Explicit operator action; never invoked during application startup. */
    @Transactional
    public WhoIcd10ImportResult importWho2019() throws IOException {
        var resource = new ClassPathResource("data/icd10/WHO_ICD10_2019_Codes.csv");
        var rows = new ArrayList<WhoRow>();
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            var format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            try (var parser = format.parse(reader)) {
                var headers = parser.getHeaderMap();
                for (String required : List.of("ICD-10 Code", "Official preferred title", "Entry type",
                        "Chapter", "Block", "Parent code", "Usage (dagger/aster)", "Long title")) {
                    // A UTF-8 BOM can occur in the first header of this release.
                    if (!headers.containsKey(required) && !(required.equals("ICD-10 Code") && headers.containsKey("\uFEFFICD-10 Code"))) {
                        throw new IllegalArgumentException("Missing WHO CSV column: " + required);
                    }
                }
                for (CSVRecord record : parser) {
                    rows.add(new WhoRow(value(record, headers.containsKey("ICD-10 Code") ? "ICD-10 Code" : "\uFEFFICD-10 Code"),
                            value(record, "Official preferred title"), value(record, "Entry type"),
                            value(record, "Chapter"), value(record, "Block"), value(record, "Parent code"),
                            value(record, "Usage (dagger/aster)"), value(record, "Long title")));
                }
            }
        }
        var byCode = new HashMap<String, WhoRow>();
        var duplicateCodes = new HashSet<String>();
        for (var row : rows) if (byCode.putIfAbsent(row.code, row) != null) duplicateCodes.add(row.code);
        var rejectedCodes = new HashSet<String>();
        for (var row : rows) {
            if (row.code.isBlank() || row.title.isBlank() || !Set.of("chapter", "block", "category").contains(row.entryType)
                    || (!row.usage.isBlank() && !Set.of("dagger", "aster").contains(row.usage))
                    || duplicateCodes.contains(row.code) || row.code.equals(row.parent)
                    || (!row.parent.isBlank() && !byCode.containsKey(row.parent))) rejectedCodes.add(row.code);
        }
        // Reject descendants of rejected parents too, so the deferred FK always resolves.
        boolean changed;
        do {
            changed = false;
            for (var row : rows) if (!row.parent.isBlank() && rejectedCodes.contains(row.parent)) changed |= rejectedCodes.add(row.code);
        } while (changed);
        int imported = 0, updated = 0, skipped = 0, rejected = 0;
        var parents = new HashSet<String>();
        for (var row : rows) if (!row.parent.isBlank()) parents.add(row.parent);
        for (var row : rows) {
            if (rejectedCodes.contains(row.code)) { rejected++; continue; }
            // Release policy: only terminal categories are selectable. Three-character
            // terminal categories remain valid. Asterisk manifestation codes require
            // the paired aetiology and are withheld pending clinical review.
            boolean selectable = row.entryType.equals("category") && !parents.contains(row.code)
                    && !row.usage.equals("aster");
            var status = repository.upsertWhoRow(row.code, row.title, nullable(row.chapter), nullable(row.block),
                    nullable(row.parent), WHO_2019_RELEASE, row.entryType, nullable(row.usage),
                    nullable(row.longTitle), selectable);
            switch (status) {
                case INSERTED -> imported++;
                case UPDATED -> updated++;
                case SKIPPED -> skipped++;
            }
        }
        return new WhoIcd10ImportResult(WHO_2019_RELEASE, imported, updated, skipped, rejected);
    }

    private static String value(CSVRecord row, String column) { return row.get(column).trim(); }
    private static String nullable(String value) { return value.isBlank() ? null : value; }
    private record WhoRow(String code, String title, String entryType, String chapter, String block,
                          String parent, String usage, String longTitle) {}
}
