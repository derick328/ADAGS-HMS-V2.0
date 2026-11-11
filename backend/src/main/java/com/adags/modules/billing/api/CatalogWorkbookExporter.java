package com.adags.modules.billing.api;

import com.adags.modules.billing.application.CatalogItemLookupPort;
import com.adags.modules.billing.application.CatalogItemView;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogWorkbookExporter {
    private final CatalogItemLookupPort lookup;

    public CatalogWorkbookExporter(CatalogItemLookupPort lookup) { this.lookup = lookup; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(OutputStream out) throws IOException {
        SXSSFWorkbook book = new SXSSFWorkbook(100);
        book.setCompressTempFiles(true);
        try {
            Sheet sheet = book.createSheet("Price Catalogue");
            CellStyle titleStyle = book.createCellStyle();
            Font bold = book.createFont(); bold.setBold(true); titleStyle.setFont(bold);
            CellStyle moneyStyle = book.createCellStyle();
            moneyStyle.setDataFormat(book.createDataFormat().getFormat("#,##0 \"TZS\""));
            Row title = sheet.createRow(0); title.createCell(0).setCellValue("ADAGS HOSPITAL Price Catalogue"); title.getCell(0).setCellStyle(titleStyle);
            sheet.createRow(1).createCell(0).setCellValue("Exported " + OffsetDateTime.now());
            String[] headers = {"Code", "Original Code", "Name", "Classification", "Type", "Category", "Current CASH price", "Currency", "Active"};
            Row header = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) { Cell cell = header.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(titleStyle); }
            sheet.createFreezePane(0, 4);
            int rowNumber = 4;
            int page = 0;
            String lastCategory = null;
            while (true) {
                var items = lookup.listItemsForExport(page++, 500);
                for (CatalogItemView item : items) {
                    String category = item.billingCategory() == null ? "Uncategorized" : item.billingCategory().name();
                    if (!category.equals(lastCategory)) {
                        Row section = sheet.createRow(rowNumber++);
                        Cell heading = section.createCell(0, CellType.STRING);
                        heading.setCellValue(category.replace('_', ' '));
                        heading.setCellStyle(titleStyle);
                        lastCategory = category;
                    }
                    Row row = sheet.createRow(rowNumber++);
                    set(row, 0, item.code()); set(row, 1, item.originalCode()); set(row, 2, item.name());
                    set(row, 3, item.classification()); set(row, 4, item.type() == null ? null : item.type().name());
                    set(row, 5, item.billingCategory() == null ? null : item.billingCategory().name());
                    if (item.currentCashPriceMinor() != null) { Cell price = row.createCell(6); price.setCellValue(item.currentCashPriceMinor()); price.setCellStyle(moneyStyle); }
                    set(row, 7, "TZS"); set(row, 8, item.isActive() ? "Active" : "Inactive");
                }
                if (items.size() < 500) break;
            }
            int[] widths = {22, 22, 45, 30, 22, 35, 22, 12, 12};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            book.write(out);
        } finally { book.dispose(); book.close(); }
    }

    private static void set(Row row, int col, String value) {
        // Explicit string cells prevent leading '=' / '+' / '@' values becoming formulas.
        row.createCell(col, CellType.STRING).setCellValue(value == null ? "" : value);
    }
}
