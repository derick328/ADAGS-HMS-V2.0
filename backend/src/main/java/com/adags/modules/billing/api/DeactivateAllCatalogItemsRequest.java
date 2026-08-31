package com.adags.modules.billing.api;

// Typed-confirmation gate. The admin must type exactly
// DeactivateAllCatalogItemsService.CONFIRMATION_PHRASE before the server
// will do anything — the same click-through-blindness guard triage's
// IMMEDIATE reason picker uses (root CLAUDE.md, frontend Safety table).
public record DeactivateAllCatalogItemsRequest(String confirmationPhrase) {
}
