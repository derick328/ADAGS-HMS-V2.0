package com.adags.modules.billing.application;

import java.util.UUID;

public record SetPharmacyItemPriceCommand(String itemCode, String itemName, long priceAmountMinor, UUID actorUserId) {
}
