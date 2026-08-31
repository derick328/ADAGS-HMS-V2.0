package com.adags.modules.billing.application;

import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

// Admin action: soft-deactivate every currently-active service_item and
// close its current price entry. Root CLAUDE.md "Data": financial
// records are never hard-deleted; this is soft-delete via is_active.
// Historical invoices keep working — they snapshot serviceItemName at
// posting time (not the current row).
//
// The typed confirmation string is enforced here rather than at the
// controller so it can't be bypassed by a direct facade call.
@Service
public class DeactivateAllCatalogItemsService {

    public static final String CONFIRMATION_PHRASE = "DEACTIVATE ALL CATALOGUE ITEMS";

    private final CatalogItemLookupPort catalogItemLookup;
    private final DomainEventPublisher eventPublisher;

    public DeactivateAllCatalogItemsService(
            CatalogItemLookupPort catalogItemLookup,
            DomainEventPublisher eventPublisher
    ) {
        this.catalogItemLookup = catalogItemLookup;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public int deactivateAll(String confirmationPhrase, UUID actorUserId) {
        if (!CONFIRMATION_PHRASE.equals(confirmationPhrase)) {
            throw new IllegalArgumentException(
                    "Missing typed confirmation. To proceed, send confirmationPhrase='" + CONFIRMATION_PHRASE + "'.");
        }
        int count = catalogItemLookup.deactivateAllActive();
        eventPublisher.publish("CatalogAllItemsDeactivated", Map.of(
                "actorUserId", actorUserId.toString(),
                "count", Integer.toString(count)));
        return count;
    }
}
