package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.laboratory.LaboratoryFacade;
import com.adags.shared.authz.AuditRecorder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class CreateCatalogItemService {
    private final NamedParameterJdbcTemplate jdbc;
    private final InventoryFacade inventory;
    private final LaboratoryFacade laboratory;
    private final AuditRecorder audit;

    public CreateCatalogItemService(NamedParameterJdbcTemplate jdbc, InventoryFacade inventory,
                                    LaboratoryFacade laboratory, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.laboratory = laboratory;
        this.audit = audit;
    }

    public record CreatedItem(UUID id, int itemCode, String productCode) {}
    public record Command(String name, CatalogItemType type, CatalogBillingCategory billingCategory,
                          String classification, String productCodePrefix, Long priceMinor) {}

    @Transactional
    public CreatedItem create(Command request, UUID actorId) {
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required (maximum 255 characters)");
        if (request.type() == null || request.billingCategory() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type and category are required");
        if (request.priceMinor() == null || request.priceMinor() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nonnegative CASH price is required");
        String prefix = normalizePrefix(request.type(), request.productCodePrefix());
        // Lock per prefix so simultaneous admin submissions cannot choose the
        // same suffix. The unique service_item.code constraint is the final guard.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(:prefix))",
                new MapSqlParameterSource("prefix", prefix), (rs, row) -> 0);
        String code = nextProductCode(prefix);
        Integer itemCode;
        do {
            itemCode = jdbc.queryForObject("SELECT nextval('manual_catalog_item_code_seq')", new MapSqlParameterSource(), Integer.class);
        } while (jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM service_item WHERE external_ref_id = :itemCode)",
                new MapSqlParameterSource("itemCode", itemCode), Boolean.class));
        String legacyCategory = switch (request.type()) {
            case PHARMACY -> "PHARMACY";
            case LABORATORY -> "LAB";
            default -> "PROCEDURE";
        };
        UUID id = jdbc.queryForObject("""
                INSERT INTO service_item (code, original_code, external_ref_id, name, category, type,
                                          billing_category, classification, is_active)
                VALUES (:code, :code, :itemCode, :name, :legacyCategory, :type,
                        :billingCategory, :classification, TRUE) RETURNING id
                """, new MapSqlParameterSource().addValue("code", code).addValue("itemCode", itemCode)
                .addValue("name", request.name().trim()).addValue("legacyCategory", legacyCategory)
                .addValue("type", request.type().name()).addValue("billingCategory", request.billingCategory().name())
                .addValue("classification", request.classification() == null || request.classification().isBlank()
                        ? null : request.classification().trim()), UUID.class);
        int priceRows = jdbc.update("""
                INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, valid_from, created_by)
                SELECT id, :itemId, :price, 'TZS', CURRENT_DATE, :actor FROM price_list WHERE payer_category = 'CASH'
                """, new MapSqlParameterSource().addValue("itemId", id).addValue("price", request.priceMinor()).addValue("actor", actorId));
        if (priceRows != 1) throw new IllegalStateException("CASH price list is missing");
        if (request.type() == CatalogItemType.PHARMACY)
            inventory.ensureCatalogueItem(code, request.name().trim(), request.classification(), actorId);
        if (request.type() == CatalogItemType.LABORATORY)
            laboratory.ensureCatalogueTest(code, request.name().trim());
        audit.record(actorId, "CATALOG_ITEM_CREATED", "service_item", id, null);
        return new CreatedItem(id, itemCode, code);
    }

    private String nextProductCode(String prefix) {
        // Check the complete prefix family, including older imported codes.
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(substring(code FROM :pattern)::integer), 0) + 1
                FROM service_item WHERE code ~ :regex
                """, new MapSqlParameterSource()
                .addValue("pattern", "[0-9]+$")
                .addValue("regex", "^" + prefix + "[0-9]+$"), Integer.class);
        return prefix + String.format(Locale.ROOT, "%04d", next);
    }

    static String normalizePrefix(CatalogItemType type, String requested) {
        String prefix = requested == null ? "" : requested.trim().toUpperCase(Locale.ROOT);
        if (prefix.isEmpty()) prefix = switch (type) {
            case LABORATORY -> "LAB";
            case SURGERY -> "GES";
            case PHARMACY -> "DRG";
            default -> type.name().substring(0, Math.min(3, type.name().length()));
        };
        boolean valid = prefix.matches("[A-Z]{2,8}") && switch (type) {
            case LABORATORY -> prefix.equals("LAB");
            case PHARMACY -> prefix.equals("DRG") || prefix.equals("DIS");
            case SURGERY -> true;
            default -> true;
        };
        if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid prefix for this item type");
        return prefix;
    }
}
