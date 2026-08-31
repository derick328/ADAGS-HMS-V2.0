package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.application.CatalogRow;
import com.adags.modules.billing.application.CatalogRowParserPort;
import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import com.adags.modules.billing.domain.InvalidCatalogFileException;
import com.adags.modules.billing.domain.InvalidCatalogFileException.RowError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Apache POI parser for the catalogue .xlsx. Header row is normalized
// (whitespace-collapsed, uppercase, header line 1 per the user spec —
// the raw file has leading/trailing spaces on PRICE etc). Every row is
// validated against the whitelist enums before it becomes a CatalogRow;
// the first pass collects all failures so the admin sees the whole
// picture rather than the first offender.
@Component
public class XlsxCatalogRowParser implements CatalogRowParserPort {

    private static final String COL_ITEM_ID = "ITEM ID";
    private static final String COL_PRODUCT_CODE = "PRODUCT CODE";
    private static final String COL_PRODUCT_NAME = "PRODUCT NAME";
    private static final String COL_CLASSIFICATION = "CLASSIFICATION";
    private static final String COL_TYPE = "TYPE";
    private static final String COL_CATEGORY = "CATEGORY";
    private static final String COL_PRICE = "PRICE";

    // Excel spellings (case-insensitive at read time) → whitelist enum.
    // Users' file uses friendly labels; the DB stores SCREAMING_SNAKE.
    private static final Map<String, CatalogItemType> TYPE_ALIASES = Map.of(
            "PHARMACY", CatalogItemType.PHARMACY,
            "PROCEDURE", CatalogItemType.PROCEDURE,
            "SURGERY", CatalogItemType.SURGERY,
            "LABORATORY", CatalogItemType.LABORATORY,
            "RADIOLOGY", CatalogItemType.RADIOLOGY,
            "OPTICAL", CatalogItemType.OPTICAL,
            "DIALYSIS", CatalogItemType.DIALYSIS,
            "OTHERS", CatalogItemType.OTHERS,
            "OTHER GENERAL CHARGES", CatalogItemType.OTHER_GENERAL_CHARGES);

    private static final Map<String, CatalogBillingCategory> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("SURGICAL CHARGES", CatalogBillingCategory.SURGICAL_CHARGES),
            Map.entry("MEDICINES AND CONSUMABLES", CatalogBillingCategory.MEDICINES_AND_CONSUMABLES),
            Map.entry("DIAGNOSTIC EXAMINATIONS", CatalogBillingCategory.DIAGNOSTIC_EXAMINATIONS),
            Map.entry("PROCEDURAL CHARGES", CatalogBillingCategory.PROCEDURAL_CHARGES),
            Map.entry("INPATIENT CHARGES", CatalogBillingCategory.INPATIENT_CHARGES),
            // Two spellings the source file uses interchangeably.
            Map.entry("REGISTRATION/CONSULTATION CHARGES", CatalogBillingCategory.REGISTRATION_CONSULTATION_CHARGES),
            Map.entry("REGISTRATION AND CONSULTATION CHARGES", CatalogBillingCategory.REGISTRATION_CONSULTATION_CHARGES),
            Map.entry("OTHER CHARGES", CatalogBillingCategory.OTHER_CHARGES),
            Map.entry("ADVANCE PAYMENT", CatalogBillingCategory.ADVANCE_PAYMENT));

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public List<CatalogRow> parse(InputStream xlsx) {
        try (Workbook workbook = new XSSFWorkbook(xlsx)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new InvalidCatalogFileException(
                        "The uploaded file has no header row.",
                        List.of(new RowError(1, "First row is empty.")));
            }
            Map<String, Integer> headerIndex = indexHeaders(headerRow);
            List<String> missing = new ArrayList<>();
            for (String required : List.of(COL_ITEM_ID, COL_PRODUCT_CODE, COL_PRODUCT_NAME, COL_TYPE, COL_CATEGORY, COL_PRICE)) {
                if (!headerIndex.containsKey(required)) missing.add(required);
            }
            if (!missing.isEmpty()) {
                throw new InvalidCatalogFileException(
                        "The uploaded file is missing required column(s): " + String.join(", ", missing),
                        List.of(new RowError(1, "Expected header row with " + String.join(", ", missing))));
            }

            List<CatalogRow> rows = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                int excelRow = i + 1;              // 1-indexed for the admin's eye
                if (isBlank(row, headerIndex))    // trailing empty rows Excel leaves behind
                    continue;
                try {
                    rows.add(parseRow(row, headerIndex, excelRow));
                } catch (RowValidationException v) {
                    errors.add(new RowError(excelRow, v.getMessage()));
                }
            }
            if (!errors.isEmpty()) {
                throw new InvalidCatalogFileException(
                        errors.size() + " row(s) rejected — fix and re-upload.", errors);
            }
            return rows;
        } catch (InvalidCatalogFileException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCatalogFileException(
                    "Could not read the .xlsx file: " + e.getMessage(),
                    List.of(new RowError(1, e.getMessage() == null ? "Unreadable workbook." : e.getMessage())));
        }
    }

    private CatalogRow parseRow(Row row, Map<String, Integer> headers, int excelRow) {
        String code = readString(row, headers, COL_PRODUCT_CODE);
        if (code == null || code.isBlank()) throw new RowValidationException("PRODUCT CODE is required.");
        String name = readString(row, headers, COL_PRODUCT_NAME);
        if (name == null || name.isBlank()) throw new RowValidationException("PRODUCT NAME is required.");

        String typeRaw = readString(row, headers, COL_TYPE);
        if (typeRaw == null || typeRaw.isBlank()) throw new RowValidationException("TYPE is required.");
        CatalogItemType type = TYPE_ALIASES.get(normalizeKey(typeRaw));
        if (type == null) {
            throw new RowValidationException("Unknown TYPE '" + typeRaw.trim() + "' (allowed: "
                    + String.join(", ", TYPE_ALIASES.keySet()) + ").");
        }

        String categoryRaw = readString(row, headers, COL_CATEGORY);
        if (categoryRaw == null || categoryRaw.isBlank()) throw new RowValidationException("CATEGORY is required.");
        CatalogBillingCategory category = CATEGORY_ALIASES.get(normalizeKey(categoryRaw));
        if (category == null) {
            throw new RowValidationException("Unknown CATEGORY '" + categoryRaw.trim() + "' (allowed: "
                    + String.join(", ", CATEGORY_ALIASES.keySet()) + ").");
        }

        // PRICE is deliberately permissive: blank, non-numeric, or negative
        // all mean "unpriced" — the metadata still lands, but no
        // price_list_entry is inserted. A later upload with a real value
        // introduces the current price. Only wildly out-of-range values
        // are rejected outright.
        //
        // Storage convention (V66 comment): TZS has no sub-unit here, so
        // amount_minor is whole shillings. The Excel PRICE column is
        // already whole shillings (source file: "5000" means TZS 5,000),
        // so the parsed number is stored as-is — no *100 conversion.
        // Multiplying by 100 landed prices at 100× the intended value
        // and broke every downstream display.
        Double priceRaw = readNumericOrNull(row, headers, COL_PRICE);
        Long priceMinor = null;
        if (priceRaw != null && priceRaw >= 0) {
            if (priceRaw > (double) Long.MAX_VALUE / 2) {
                throw new RowValidationException("PRICE is implausibly large — check the file's units.");
            }
            priceMinor = Math.round(priceRaw);
        }

        Integer externalRefId = null;
        Double idRaw = readNumeric(row, headers, COL_ITEM_ID);
        if (idRaw != null) externalRefId = (int) Math.round(idRaw);

        String classification = readString(row, headers, COL_CLASSIFICATION);
        String trimmedCode = code.trim();
        return new CatalogRow(
                excelRow, externalRefId,
                trimmedCode, trimmedCode, name.trim(),
                classification == null ? null : classification.trim(),
                type, category, priceMinor);
    }

    // Case-insensitive header lookup with whitespace collapsed — the raw
    // file has " PRICE " with leading/trailing spaces (user spec).
    private Map<String, Integer> indexHeaders(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = header.getFirstCellNum(); i < header.getLastCellNum(); i++) {
            Cell cell = header.getCell(i);
            if (cell == null) continue;
            String norm = normalizeKey(dataFormatter.formatCellValue(cell));
            if (!norm.isEmpty()) map.put(norm, i);
        }
        return map;
    }

    private String readString(Row row, Map<String, Integer> headers, String col) {
        Integer idx = headers.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return dataFormatter.formatCellValue(cell);
    }

    private Double readNumeric(Row row, Map<String, Integer> headers, String col) {
        Integer idx = headers.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        String text = dataFormatter.formatCellValue(cell).replace(",", "").trim();
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new RowValidationException("Value in column " + col + " is not numeric: '" + text + "'.");
        }
    }

    // Same as readNumeric but silently returns null on a non-numeric cell
    // — used for PRICE, where a blank/garbage value is legitimately
    // treated as "unpriced" rather than a rejection.
    private Double readNumericOrNull(Row row, Map<String, Integer> headers, String col) {
        Integer idx = headers.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        String text = dataFormatter.formatCellValue(cell).replace(",", "").trim();
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(Row row, Map<String, Integer> headers) {
        for (Integer idx : headers.values()) {
            Cell cell = row.getCell(idx);
            if (cell != null && !dataFormatter.formatCellValue(cell).isBlank()) return false;
        }
        return true;
    }

    private static String normalizeKey(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    // Local checked-ish token for row-level failures — never leaks outside
    // parse(). Callers wrap into InvalidCatalogFileException.RowError.
    private static class RowValidationException extends RuntimeException {
        RowValidationException(String msg) {
            super(msg);
        }
    }
}
