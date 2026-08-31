package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Duplicate PRODUCT CODE handling. Deliberately a plain unit test —
// resolver is a pure function over CatalogRow, no DB or Spring context
// needed to prove the effective-code rules.
class CatalogCodeResolverTest {

    @Test
    void keepsRawCodeWhenNothingCollides() {
        CatalogCodeResolver.Resolution result = CatalogCodeResolver.resolve(List.of(
                row(2, 101, "LAB0098", "Alpha Feto Protein"),
                row(3, 102, "LAB0072", "Serum Uric Acid")));

        assertThat(result.duplicates()).isEmpty();
        assertThat(result.resolvedRows())
                .extracting(CatalogRow::code)
                .containsExactly("LAB0098", "LAB0072");
        assertThat(result.resolvedRows())
                .extracting(CatalogRow::originalCode)
                .containsExactly("LAB0098", "LAB0072");
    }

    @Test
    void suffixesSecondAndLaterOccurrencesWithExternalRefId() {
        CatalogCodeResolver.Resolution result = CatalogCodeResolver.resolve(List.of(
                row(2, 4519, "DIS0001", "Ankle Support L"),
                row(3, 4520, "DIS0001", "Ankle Support XL"),
                row(4, 4521, "DIS0001", "Knee Support XXL")));

        assertThat(result.resolvedRows())
                .extracting(CatalogRow::code)
                .containsExactly("DIS0001", "DIS0001#4520", "DIS0001#4521");
        assertThat(result.resolvedRows())
                .extracting(CatalogRow::originalCode)
                .containsExactly("DIS0001", "DIS0001", "DIS0001");
        assertThat(result.duplicates()).hasSize(1);
        assertThat(result.duplicates().get(0).originalCode()).isEqualTo("DIS0001");
        assertThat(result.duplicates().get(0).occurrences())
                .extracting(CatalogCodeResolver.DuplicateOccurrence::effectiveCode)
                .containsExactly("DIS0001", "DIS0001#4520", "DIS0001#4521");
    }

    @Test
    void fallsBackToExcelRowSuffixWhenExternalRefIdIsMissing() {
        CatalogCodeResolver.Resolution result = CatalogCodeResolver.resolve(List.of(
                row(2, null, "PRO", "Melcott catheter insertion"),
                row(11, null, "PRO", "Polypectomy")));

        assertThat(result.resolvedRows())
                .extracting(CatalogRow::code)
                .containsExactly("PRO", "PRO#row11");
    }

    @Test
    void isDeterministicAcrossRepeatedResolutions() {
        List<CatalogRow> input = List.of(
                row(2, 4519, "DIS0001", "Ankle Support L"),
                row(3, 4520, "DIS0001", "Ankle Support XL"));

        List<String> first = CatalogCodeResolver.resolve(input).resolvedRows()
                .stream().map(CatalogRow::code).toList();
        List<String> second = CatalogCodeResolver.resolve(input).resolvedRows()
                .stream().map(CatalogRow::code).toList();

        assertThat(second).isEqualTo(first);
    }

    private static CatalogRow row(int excelRow, Integer externalRefId, String code, String name) {
        return new CatalogRow(
                excelRow, externalRefId, code, code, name, null,
                CatalogItemType.PHARMACY, CatalogBillingCategory.MEDICINES_AND_CONSUMABLES, 1000L);
    }
}
