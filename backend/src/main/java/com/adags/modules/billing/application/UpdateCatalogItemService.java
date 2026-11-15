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
import java.util.Objects;
import java.util.UUID;

@Service
public class UpdateCatalogItemService {
    @io.swagger.v3.oas.annotations.media.Schema(name = "CatalogItemDetails")
    public record Details(String name, CatalogItemType type, CatalogBillingCategory billingCategory,
                          String classification, boolean isActive, @io.swagger.v3.oas.annotations.media.Schema(nullable = true, minimum = "0") Long priceMinor) {}
    @io.swagger.v3.oas.annotations.media.Schema(name = "UpdateCatalogItemRequest")
    public record Command(Details expected, Details details) {}
    private final NamedParameterJdbcTemplate jdbc;
    private final InventoryFacade inventory;
    private final LaboratoryFacade laboratory;
    private final AuditRecorder audit;

    public UpdateCatalogItemService(NamedParameterJdbcTemplate jdbc, InventoryFacade inventory,
                                    LaboratoryFacade laboratory, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.laboratory = laboratory;
        this.audit = audit;
    }

    @Transactional
    public void update(UUID id, Command command, UUID actor) {
        if (command == null || command.expected() == null || command.details() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item details and previous values are required");
        Details next = command.details();
        if (next.name() == null || next.name().isBlank() || next.name().trim().length() > 255
                || next.type() == null || next.billingCategory() == null
                || (next.priceMinor() != null && next.priceMinor() < 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name, type, category and an optional nonnegative CASH price");
        var params = new MapSqlParameterSource("id", id);
        var rows = jdbc.query("SELECT * FROM service_item WHERE id = :id FOR UPDATE", params,
                (rs, row) -> new Details(rs.getString("name"),
                        rs.getString("type") == null ? null : CatalogItemType.valueOf(rs.getString("type")),
                        rs.getString("billing_category") == null ? null : CatalogBillingCategory.valueOf(rs.getString("billing_category")),
                        rs.getString("classification"), rs.getBoolean("is_active"), null));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalogue item not found");
        var prices = jdbc.query("""
                SELECT ple.amount_minor FROM price_list_entry ple JOIN price_list pl ON pl.id = ple.price_list_id
                WHERE ple.service_item_id = :id AND pl.payer_category = 'CASH' AND ple.valid_to IS NULL
                """, params, (rs, row) -> rs.getLong("amount_minor"));
        Long currentPrice = prices.isEmpty() ? null : prices.get(0);
        Details current = rows.get(0);
        Details expected = command.expected();
        if (!Objects.equals(current.name(), expected.name()) || current.type() != expected.type()
                || current.billingCategory() != expected.billingCategory()
                || !Objects.equals(current.classification(), expected.classification())
                || current.isActive() != expected.isActive() || !Objects.equals(currentPrice, expected.priceMinor()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item changed; refresh the catalogue before saving");
        String code = jdbc.queryForObject("SELECT code FROM service_item WHERE id = :id", params, String.class);
        params.addValue("name", next.name().trim()).addValue("type", next.type().name())
                .addValue("billingCategory", next.billingCategory().name())
                .addValue("classification", next.classification() == null || next.classification().isBlank() ? null : next.classification().trim())
                .addValue("active", next.isActive()).addValue("actor", actor).addValue("price", next.priceMinor());
        String category = switch (next.type()) {
            case PHARMACY -> "PHARMACY";
            case LABORATORY -> "LAB";
            default -> "PROCEDURE";
        };
        params.addValue("category", category);
        jdbc.update("""
                UPDATE service_item SET name = :name, type = :type, billing_category = :billingCategory,
                classification = :classification, is_active = :active,
                category = CASE WHEN category = 'CONSULTATION' THEN category ELSE :category END WHERE id = :id
                """, params);
        if (!Objects.equals(currentPrice, next.priceMinor())) {
            jdbc.update("""
                    UPDATE price_list_entry SET valid_to = CURRENT_DATE WHERE service_item_id = :id
                    AND valid_to IS NULL AND price_list_id IN (SELECT id FROM price_list WHERE payer_category = 'CASH')
                    """, params);
            if (next.priceMinor() != null) {
                int count = jdbc.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, valid_from, created_by)
                        SELECT id, :id, :price, 'TZS', CURRENT_DATE, :actor FROM price_list WHERE payer_category = 'CASH'
                        """, params);
                if (count != 1) throw new IllegalStateException("CASH price list is missing");
            }
            audit.record(actor, "CATALOG_PRICE_CHANGED", "service_item", id, null);
        }
        if (next.type() == CatalogItemType.PHARMACY) inventory.ensureCatalogueItem(code, next.name().trim(), next.classification(), actor);
        if (next.type() == CatalogItemType.LABORATORY) laboratory.ensureCatalogueTest(code, next.name().trim());
        boolean nameChanged = !Objects.equals(current.name(), next.name().trim());
        boolean classificationChanged = !Objects.equals(current.classification(), params.getValue("classification"));
        if (nameChanged || classificationChanged)
            inventory.updateCatalogueDetails(code, nameChanged ? next.name().trim() : null, next.classification(), classificationChanged);
        if (nameChanged) laboratory.updateCatalogueName(code, next.name().trim());
        audit.record(actor, "CATALOG_ITEM_UPDATED", "service_item", id, null);
    }
}
