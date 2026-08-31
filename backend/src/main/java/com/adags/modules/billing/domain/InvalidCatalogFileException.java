package com.adags.modules.billing.domain;

import java.util.List;

// The parser detected structural or whitelist failures — every offending
// row is named. The importer treats the whole file as one atomic unit:
// even one rejection blocks Apply. Root CLAUDE.md "Data": no silent
// insert of an unknown TYPE/CATEGORY value.
public class InvalidCatalogFileException extends RuntimeException {

    public record RowError(int excelRow, String reason) {
    }

    private final List<RowError> errors;

    public InvalidCatalogFileException(String summary, List<RowError> errors) {
        super(summary);
        this.errors = List.copyOf(errors);
    }

    public List<RowError> errors() {
        return errors;
    }
}
