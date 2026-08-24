package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Supplier;

import java.util.UUID;

public record SupplierResponse(UUID id, String code, String name) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.id(), supplier.code(), supplier.name());
    }
}
