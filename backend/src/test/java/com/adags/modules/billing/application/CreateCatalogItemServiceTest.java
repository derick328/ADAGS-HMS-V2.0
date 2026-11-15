package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogItemType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateCatalogItemServiceTest {
    @Test
    void generatesExpectedPrefixesAndAcceptsCustomSurgeryFamilies() {
        assertThat(CreateCatalogItemService.normalizePrefix(CatalogItemType.LABORATORY, null)).isEqualTo("LAB");
        assertThat(CreateCatalogItemService.normalizePrefix(CatalogItemType.SURGERY, "gyn")).isEqualTo("GYN");
        assertThat(CreateCatalogItemService.normalizePrefix(CatalogItemType.SURGERY, "den")).isEqualTo("DEN");
        assertThat(CreateCatalogItemService.normalizePrefix(CatalogItemType.PHARMACY, "dis")).isEqualTo("DIS");
    }

    @Test
    void rejectsPharmacyOrLabPrefixFromAnotherFamily() {
        assertThatThrownBy(() -> CreateCatalogItemService.normalizePrefix(CatalogItemType.PHARMACY, "GES"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CreateCatalogItemService.normalizePrefix(CatalogItemType.LABORATORY, "DRG"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
