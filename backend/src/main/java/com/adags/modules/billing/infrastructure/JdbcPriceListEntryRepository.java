package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceListEntry;
import com.adags.modules.billing.domain.PriceListEntryRepository;
import com.adags.shared.kernel.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPriceListEntryRepository implements PriceListEntryRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, price_list_id, service_item_id, amount_minor, currency_code, valid_from, valid_to,
                   created_by, created_at
            FROM price_list_entry
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPriceListEntryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PriceListEntry> findCurrent(UUID serviceItemId, PayerCategory payerCategory) {
        String sql = """
                SELECT ple.id, ple.price_list_id, ple.service_item_id, ple.amount_minor, ple.currency_code,
                       ple.valid_from, ple.valid_to, ple.created_by, ple.created_at
                FROM price_list_entry ple
                JOIN price_list pl ON pl.id = ple.price_list_id
                WHERE ple.service_item_id = :serviceItemId
                  AND pl.payer_category = :payerCategory
                  AND ple.valid_to IS NULL
                """;
        List<PriceListEntry> results = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("serviceItemId", serviceItemId)
                .addValue("payerCategory", payerCategory.name()),
                this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public PriceListEntry insert(PriceListEntry entry) {
        String sql = """
                INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, created_by)
                VALUES (:priceListId, :serviceItemId, :amountMinor, :currencyCode, :createdBy)
                RETURNING id, valid_from, created_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("priceListId", entry.priceListId())
                .addValue("serviceItemId", entry.serviceItemId())
                .addValue("amountMinor", entry.amount().amountMinor())
                .addValue("currencyCode", entry.amount().currencyCode())
                .addValue("createdBy", entry.createdBy());

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new PriceListEntry(
                (UUID) rs.getObject("id"), entry.priceListId(), entry.serviceItemId(), entry.amount(),
                rs.getDate("valid_from").toLocalDate(), null, entry.createdBy(),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public void close(UUID entryId, java.time.LocalDate validTo) {
        jdbcTemplate.update("UPDATE price_list_entry SET valid_to = :validTo WHERE id = :id",
                new MapSqlParameterSource().addValue("id", entryId).addValue("validTo", validTo));
    }

    private PriceListEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        Date validTo = rs.getDate("valid_to");
        return new PriceListEntry(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("price_list_id"),
                (UUID) rs.getObject("service_item_id"),
                new Money(rs.getLong("amount_minor"), rs.getString("currency_code")),
                rs.getDate("valid_from").toLocalDate(),
                validTo == null ? null : validTo.toLocalDate(),
                (UUID) rs.getObject("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }
}
