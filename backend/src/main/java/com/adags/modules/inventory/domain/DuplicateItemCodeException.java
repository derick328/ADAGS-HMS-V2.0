package com.adags.modules.inventory.domain;

public class DuplicateItemCodeException extends RuntimeException {

    public DuplicateItemCodeException(String code) {
        super("An item with code '" + code + "' already exists.");
    }
}
