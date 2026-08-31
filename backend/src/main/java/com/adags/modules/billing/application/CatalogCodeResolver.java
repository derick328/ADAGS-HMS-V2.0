package com.adags.modules.billing.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Turns a raw parse of the catalogue file — which may reuse the same
// PRODUCT CODE across many unrelated items — into a list of rows whose
// effective codes are unique. The first occurrence of any raw code keeps
// it as-is; each subsequent occurrence gets a "#<externalRefId>" suffix
// (or "#row<excelRowNumber>" if externalRefId is null). Both suffix
// candidates are guaranteed unique within a single file, so no further
// disambiguation is needed inside one import.
//
// Deterministic: the same input file always produces the same effective
// codes. Re-uploading a byte-identical file lands the same rows on the
// same service_item.code values → the upsert path is idempotent.
//
// The raw code is preserved on CatalogRow#originalCode so the admin UI
// and audit can trace a stored row back to the exact source value; the
// preview also surfaces a DuplicateCode entry per collision so the admin
// sees what was disambiguated before applying.
public final class CatalogCodeResolver {

    private CatalogCodeResolver() {}

    public record Resolution(List<CatalogRow> resolvedRows, List<DuplicateCode> duplicates) {}

    // One duplicate group: the raw PRODUCT CODE that repeated, and the
    // per-row rewrite the admin can see in the preview.
    public record DuplicateCode(String originalCode, List<DuplicateOccurrence> occurrences) {}

    public record DuplicateOccurrence(int excelRow, String effectiveCode, String name) {}

    public static Resolution resolve(List<CatalogRow> rows) {
        // Preserve encounter order — the earliest occurrence keeps the raw
        // code, later occurrences get suffixed. LinkedHashMap so duplicates
        // report in the order the admin scrolled past them in the file.
        Map<String, List<CatalogRow>> byOriginal = new LinkedHashMap<>();
        for (CatalogRow row : rows) {
            byOriginal.computeIfAbsent(row.originalCode(), k -> new ArrayList<>()).add(row);
        }

        Set<String> usedEffective = new HashSet<>();
        Map<Integer, String> effectiveByExcelRow = new HashMap<>();
        List<DuplicateCode> duplicates = new ArrayList<>();

        for (Map.Entry<String, List<CatalogRow>> entry : byOriginal.entrySet()) {
            String originalCode = entry.getKey();
            List<CatalogRow> group = entry.getValue();
            if (group.size() == 1) {
                CatalogRow only = group.get(0);
                String effective = uniqueOrThrow(originalCode, only, usedEffective);
                effectiveByExcelRow.put(only.excelRowNumber(), effective);
                continue;
            }
            List<DuplicateOccurrence> occurrences = new ArrayList<>();
            for (int i = 0; i < group.size(); i++) {
                CatalogRow row = group.get(i);
                String candidate = (i == 0) ? originalCode : suffix(originalCode, row);
                String effective = uniqueOrThrow(candidate, row, usedEffective);
                effectiveByExcelRow.put(row.excelRowNumber(), effective);
                occurrences.add(new DuplicateOccurrence(row.excelRowNumber(), effective, row.name()));
            }
            duplicates.add(new DuplicateCode(originalCode, occurrences));
        }

        List<CatalogRow> resolved = new ArrayList<>(rows.size());
        for (CatalogRow row : rows) {
            String effective = effectiveByExcelRow.get(row.excelRowNumber());
            resolved.add(effective.equals(row.code()) ? row : row.withEffectiveCode(effective));
        }
        return new Resolution(resolved, duplicates);
    }

    // externalRefId is guaranteed unique in-file when populated (the source
    // spreadsheet uses it as a row PK); when it isn't populated we fall
    // back to the Excel row number, which is also unique in-file.
    private static String suffix(String originalCode, CatalogRow row) {
        return originalCode + "#" + (row.externalRefId() != null
                ? row.externalRefId()
                : "row" + row.excelRowNumber());
    }

    private static String uniqueOrThrow(String candidate, CatalogRow row, Set<String> used) {
        if (used.add(candidate)) return candidate;
        // Extremely unlikely — would require the raw code to also equal
        // an already-suffixed value (e.g. a source row literally named
        // "DIS0001#4520"). Guard with an explicit failure rather than
        // silently clobbering.
        throw new IllegalStateException(
                "Cannot generate a unique effective code for row " + row.excelRowNumber()
                        + " (raw code '" + row.originalCode() + "'): candidate '" + candidate + "' already used.");
    }
}
