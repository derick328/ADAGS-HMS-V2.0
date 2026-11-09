package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.application.CatalogItemLookupPort;
import com.adags.modules.billing.application.CatalogItemQuery;
import com.adags.modules.billing.application.CatalogItemView;
import com.adags.modules.billing.application.CatalogRow;
import com.adags.modules.billing.domain.CatalogBillingCategory;
import com.adags.modules.billing.domain.CatalogItemType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Read-model + write path for the catalogue importer. Kept separate from
// JdbcServiceItemRepository so that repo's narrow contract stays
// untouched — only the importer needs the extended metadata columns
// alongside the current CASH price.
@Repository
public class JdbcCatalogItemLookup implements CatalogItemLookupPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCatalogItemLookup(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_COLS = """
            SELECT si.id, si.code, si.original_code, si.name, si.classification, si.type, si.billing_category,
                   si.is_active,
                   ple.amount_minor AS current_cash_price
              FROM service_item si
              LEFT JOIN price_list pl ON pl.payer_category = 'CASH'
              LEFT JOIN price_list_entry ple
                     ON ple.service_item_id = si.id
                    AND ple.price_list_id = pl.id
                    AND ple.valid_to IS NULL
            """;

    @Override
    public List<CatalogItemView> findByCodes(List<String> codes) {
        if (codes.isEmpty()) return List.of();
        String sql = SELECT_COLS + " WHERE si.code IN (:codes)";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("codes", codes), JdbcCatalogItemLookup::mapRow);
    }

    @Override
    public UUID insertNewCatalogItem(CatalogRow row) {
        // Derive the existing coarse category from Excel TYPE so existing
        // callers (BillingFacade, pharmacy postServiceCharge) keep working.
        // PHARMACY -> PHARMACY, LABORATORY -> LAB, everything else -> PROCEDURE.
        // CONSULTATION isn't reachable from the Excel vocabulary; existing
        // seed rows (CONSULT_GENERAL, CONSULT_SPECIALIST) keep their
        // pre-existing 'CONSULTATION' category — this path is inserts only.
        String legacyCategory = switch (row.type()) {
            case PHARMACY -> "PHARMACY";
            case LABORATORY -> "LAB";
            default -> "PROCEDURE";
        };
        String sql = """
                INSERT INTO service_item (code, original_code, name, category, type, billing_category, classification, external_ref_id, is_active)
                VALUES (:code, :originalCode, :name, :legacyCategory, :type, :billingCategory, :classification, :externalRefId, TRUE)
                RETURNING id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", row.code())
                .addValue("originalCode", row.originalCode())
                .addValue("name", row.name())
                .addValue("legacyCategory", legacyCategory)
                .addValue("type", row.type().name())
                .addValue("billingCategory", row.billingCategory().name())
                .addValue("classification", nullIfBlank(row.classification()))
                .addValue("externalRefId", row.externalRefId());
        return jdbcTemplate.queryForObject(sql, params, UUID.class);
    }

    @Override
    public void updateMetadata(UUID serviceItemId, CatalogRow row) {
        // 'category' deliberately not touched — see the insertNewCatalogItem
        // comment. Existing consumers of service_item.category (billing,
        // pharmacy) key off that value and re-classifying via an Excel
        // re-upload would silently break them.
        //
        // is_active = TRUE reactivates rows that were soft-deactivated via
        // the "Deactivate all" action: an admin doing a full reset and
        // then re-uploading the fresh catalogue expects those PRODUCT
        // CODEs to come back on. New items always insert with
        // is_active = TRUE (insertNewCatalogItem), so this keeps the two
        // paths symmetrical.
        String sql = """
                UPDATE service_item
                   SET name = :name,
                       original_code = :originalCode,
                       type = :type,
                       billing_category = :billingCategory,
                       classification = :classification,
                       external_ref_id = :externalRefId,
                       is_active = TRUE
                 WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", serviceItemId)
                .addValue("name", row.name())
                .addValue("originalCode", row.originalCode())
                .addValue("type", row.type().name())
                .addValue("billingCategory", row.billingCategory().name())
                .addValue("classification", nullIfBlank(row.classification()))
                .addValue("externalRefId", row.externalRefId());
        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<CatalogItemView> listItems(CatalogItemQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(query, params);
        String sql = SELECT_COLS + where +
                " ORDER BY si.name LIMIT :limit OFFSET :offset";
        params.addValue("limit", query.pageSize());
        params.addValue("offset", query.page() * query.pageSize());
        return jdbcTemplate.query(sql, params, JdbcCatalogItemLookup::mapRow);
    }

    @Override
    public int countItems(CatalogItemQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(query, params);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_item si" + where, params, Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<CatalogItemView> findUnclassified(
            CatalogItemType type, String query, Collection<String> excludeCodes, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", type.name())
                .addValue("limit", Math.max(1, limit));
        StringBuilder sql = new StringBuilder(SELECT_COLS);
        sql.append(" WHERE si.is_active = TRUE AND si.type = :type");
        if (excludeCodes != null && !excludeCodes.isEmpty()) {
            sql.append(" AND si.code NOT IN (:excludeCodes)");
            params.addValue("excludeCodes", excludeCodes);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (si.name ILIKE :pattern OR si.code ILIKE :pattern OR si.original_code ILIKE :pattern)");
            params.addValue("pattern", "%" + query.trim() + "%");
        }
        sql.append(" ORDER BY si.name LIMIT :limit");
        return jdbcTemplate.query(sql.toString(), params, JdbcCatalogItemLookup::mapRow);
    }

    @Override
    public int deactivateAllActive() {
        // Core consultation items are managed by migrations, not Excel imports.
        // Triage must keep their prices available after a catalogue reset.
        // Other items keep the normal versioned price-close behaviour.
        jdbcTemplate.update(
                "UPDATE price_list_entry SET valid_to = CURRENT_DATE " +
                "  WHERE valid_to IS NULL " +
                "    AND service_item_id IN (SELECT id FROM service_item WHERE is_active = TRUE " +
                "      AND code NOT IN ('CONSULT_CLINICAL_OFFICER', 'CONSULT_GENERAL', 'CONSULT_SPECIALIST'))",
                new MapSqlParameterSource());
        return jdbcTemplate.update(
                "UPDATE service_item SET is_active = FALSE WHERE is_active = TRUE " +
                "  AND code NOT IN ('CONSULT_CLINICAL_OFFICER', 'CONSULT_GENERAL', 'CONSULT_SPECIALIST')",
                new MapSqlParameterSource());
    }

    // Trigram similarity is the ideal match on 'name' (V67 index), but a
    // plain ILIKE %q% already hits the same GIN index and produces the
    // ordering the admin expects (name asc). Sticking with ILIKE keeps
    // this readable and lets the trigram threshold stay a future tuning
    // via protocol_setting rather than an ad-hoc constant here.
    private static String buildWhere(CatalogItemQuery query, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (query.activeOnly()) {
            where.append(" AND si.is_active = TRUE ");
        }
        if (query.query() != null && !query.query().isBlank()) {
            // Admin browse: match against name, the effective storage
            // code, AND the raw source code — so a search for "DIS0001"
            // still lands every disambiguated "DIS0001#4519" row.
            where.append(" AND (si.name ILIKE :pattern OR si.code ILIKE :pattern OR si.original_code ILIKE :pattern) ");
            params.addValue("pattern", "%" + query.query().trim() + "%");
        }
        if (query.type() != null) {
            where.append(" AND si.type = :type ");
            params.addValue("type", query.type().name());
        }
        if (query.billingCategory() != null) {
            where.append(" AND si.billing_category = :billingCategory ");
            params.addValue("billingCategory", query.billingCategory().name());
        }
        return where.toString();
    }

    private static CatalogItemView mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long price = rs.getObject("current_cash_price") == null ? null : rs.getLong("current_cash_price");
        String type = rs.getString("type");
        String billingCategory = rs.getString("billing_category");
        return new CatalogItemView(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("original_code"),
                rs.getString("name"),
                rs.getString("classification"),
                type == null ? null : CatalogItemType.valueOf(type),
                billingCategory == null ? null : CatalogBillingCategory.valueOf(billingCategory),
                price,
                rs.getBoolean("is_active"));
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
