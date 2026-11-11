package com.adags.modules.billing;

import com.adags.modules.billing.api.CatalogWorkbookExporter;
import com.adags.modules.billing.application.CatalogItemLookupPort;
import com.adags.modules.billing.application.CatalogItemView;
import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogWorkbookExporterTest {
    @Test
    void exportsAllPagesWithSectionsAndSafeCells() throws Exception {
        List<CatalogItemView> firstPage = new ArrayList<>();
        for (int i = 0; i < 500; i++) firstPage.add(item("LAB-" + i, 1500L));
        CatalogItemLookupPort lookup = lookup(firstPage, List.of(item("=SAFE", 1800L)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CatalogWorkbookExporter(lookup).export(out);

        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var sheet = book.getSheet("Price Catalogue");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Code");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).contains("DIAGNOSTIC");
            assertThat(sheet.getRow(505).getCell(0).getStringCellValue()).isEqualTo("=SAFE");
            assertThat(sheet.getRow(505).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(505).getCell(6).getNumericCellValue()).isEqualTo(1800);
        }
    }

    @Test
    void emptyCatalogueStillProducesReadableWorkbook() throws Exception {
        CatalogItemLookupPort lookup = lookup(List.of(), List.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CatalogWorkbookExporter(lookup).export(out);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(book.getSheet("Price Catalogue").getRow(3).getCell(0).getStringCellValue()).isEqualTo("Code");
        }
    }

    private CatalogItemView item(String code, long price) {
        return new CatalogItemView(UUID.randomUUID(), code, code, "Test", null,
                CatalogItemType.LABORATORY, CatalogBillingCategory.DIAGNOSTIC_EXAMINATIONS, price, true);
    }

    private CatalogItemLookupPort lookup(List<CatalogItemView> first, List<CatalogItemView> second) {
        return (CatalogItemLookupPort) Proxy.newProxyInstance(CatalogItemLookupPort.class.getClassLoader(),
                new Class<?>[]{CatalogItemLookupPort.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("listItemsForExport")) throw new UnsupportedOperationException(method.getName());
                    return (int) args[0] == 0 ? first : second;
                });
    }
}
