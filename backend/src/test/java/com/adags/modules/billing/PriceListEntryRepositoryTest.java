package com.adags.modules.billing;

import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceListEntry;
import com.adags.modules.billing.domain.PriceListEntryRepository;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 02-billing-architecture.md §4.2: entry-level versioning. Read-only
// assertions use the real seeded CONSULT_GENERAL/CASH row (V15); tests
// that mutate a price use their own fixture service_item so the shared
// seed data other tests depend on is never left in a different state.
class PriceListEntryRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private PriceListEntryRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // No @AfterEach truncate here — price_list_entry and service_item hold
    // the migration-seeded CONSULT_GENERAL/CONSULT_SPECIALIST CASH prices
    // other tests in this suite depend on (PriceResolutionServiceTest, and
    // eventually the triage charge-posting path). Truncating either would
    // repeat the exact class of bug the protocol_setting/department
    // TRUNCATE CASCADE incident already taught this codebase — see
    // backend/CLAUDE.md. Fixture rows here use randomly-suffixed codes and
    // are left in place; harmless accumulation, same reasoning as leaving
    // fixture department rows uncleaned elsewhere.

    @Test
    void findCurrentResolvesTheSeededCashConsultGeneralPrice() {
        UUID serviceItemId = seededServiceItemId("CONSULT_GENERAL");

        PriceListEntry current = repository.findCurrent(serviceItemId, PayerCategory.CASH).orElseThrow();

        assertThat(current.amount()).isEqualTo(new Money(8_000, "TZS"));
        assertThat(current.validTo()).isNull();
    }

    @Test
    void findCurrentResolvesTheNhifConsultGeneralPrice() {
        UUID serviceItemId = seededServiceItemId("CONSULT_GENERAL");

        assertThat(repository.findCurrent(serviceItemId, PayerCategory.NHIF).orElseThrow().amount())
                .isEqualTo(new Money(8_000, "TZS"));
    }

    // "A change closes the current entry and opens a new one" — proven by
    // closing a fixture's current row and inserting a replacement, then
    // confirming findCurrent follows the new one and the old row persists.
    @Test
    void reprisingClosesTheOldRowAndFindCurrentFollowsTheNewOne() {
        UUID admin = bootstrapAdminId();
        UUID serviceItemId = insertFixtureServiceItem();
        UUID priceListId = priceListId(PayerCategory.CASH);
        PriceListEntry original = repository.insert(new PriceListEntry(
                null, priceListId, serviceItemId, new Money(5_000, "TZS"), null, null, admin, null));

        jdbcTemplate.update("UPDATE price_list_entry SET valid_to = CURRENT_DATE WHERE id = :id",
                new MapSqlParameterSource("id", original.id()));
        PriceListEntry repriced = repository.insert(new PriceListEntry(
                null, priceListId, serviceItemId, new Money(7_500, "TZS"), null, null, admin, null));

        PriceListEntry current = repository.findCurrent(serviceItemId, PayerCategory.CASH).orElseThrow();
        assertThat(current.id()).isEqualTo(repriced.id());
        assertThat(current.amount()).isEqualTo(new Money(7_500, "TZS"));

        // The old row still exists — versioned, not deleted (root CLAUDE.md "Data").
        Integer historicalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_list_entry WHERE id = :id AND valid_to IS NOT NULL",
                new MapSqlParameterSource("id", original.id()), Integer.class);
        assertThat(historicalCount).isEqualTo(1);
    }

    // The partial unique index — the real guard behind "never edited in
    // place", independent of application code remembering to close the old row first.
    @Test
    void aSecondCurrentEntryForTheSameItemAndListIsRejected() {
        UUID admin = bootstrapAdminId();
        UUID serviceItemId = insertFixtureServiceItem();
        UUID priceListId = priceListId(PayerCategory.CASH);
        repository.insert(new PriceListEntry(null, priceListId, serviceItemId, new Money(5_000, "TZS"), null, null, admin, null));

        assertThatThrownBy(() -> repository.insert(new PriceListEntry(
                null, priceListId, serviceItemId, new Money(6_000, "TZS"), null, null, admin, null)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertFixtureServiceItem() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO service_item (code, name, category)
                        VALUES (:code, 'Test Item', 'CONSULTATION')
                        RETURNING id
                        """,
                new MapSqlParameterSource("code", "TEST-ITEM-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
    }

    private UUID seededServiceItemId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM service_item WHERE code = :code",
                new MapSqlParameterSource("code", code), UUID.class);
    }

    private UUID priceListId(PayerCategory payerCategory) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM price_list WHERE payer_category = :payerCategory",
                new MapSqlParameterSource("payerCategory", payerCategory.name()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
