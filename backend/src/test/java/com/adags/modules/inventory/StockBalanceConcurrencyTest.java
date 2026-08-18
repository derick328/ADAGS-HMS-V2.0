package com.adags.modules.inventory;

import com.adags.modules.inventory.domain.InsufficientStockException;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// root CLAUDE.md "Concurrency": "CHECK (quantity >= 0) -- stock balances"
// and "A read-then-write check in application code loses the race." Fires
// real concurrent transactions against the same (item, batch, store) row
// and proves the database CHECK — not application logic — is what stops
// oversubscription (12-pharmacist.md §5.5: "two pharmacists dispensing the
// same batch simultaneously must not both succeed").
class StockBalanceConcurrencyTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE stock_balance, stock_batch, item, store, supplier CASCADE",
                new MapSqlParameterSource());
    }

    @Test
    void concurrentDecrementsBeyondAvailableQuantityLeaveTheBalanceNeverNegative() throws Exception {
        UUID itemId = insertFixtureItem();
        UUID storeId = insertFixtureStore();
        UUID batchId = insertFixtureBatch(itemId, storeId);

        stockBalanceRepository.applyDelta(itemId, batchId, storeId, 10);

        int racers = 5; // each tries to take 3 from a pool of 10 -> at most 3 can succeed
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = IntStream.range(0, racers)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            return stockBalanceRepository.applyDelta(itemId, batchId, storeId, -3);
                        } catch (InsufficientStockException ex) {
                            return null;
                        }
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long successes = futures.stream().map(this::resolve).filter(r -> r != null).count();
            assertThat(successes).isEqualTo(3);
        } finally {
            pool.shutdown();
        }

        Integer finalBalance = jdbcTemplate.queryForObject(
                "SELECT quantity FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", batchId), Integer.class);
        assertThat(finalBalance).isEqualTo(1);
    }

    // Guards the DECREMENT_SQL branch specifically: a negative delta must
    // never fabricate a balance row for a batch nobody ever received stock
    // into at this store (as an upsert with a clamped candidate value
    // would silently do) — it has zero stock, full stop.
    @Test
    void decrementingABatchWithNoBalanceRowIsRejected() {
        UUID itemId = insertFixtureItem();
        UUID storeId = insertFixtureStore();
        UUID batchId = insertFixtureBatch(itemId, storeId);

        assertThatThrownBy(() -> stockBalanceRepository.applyDelta(itemId, batchId, storeId, -1))
                .isInstanceOf(InsufficientStockException.class);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_balance WHERE batch_id = :batchId",
                new MapSqlParameterSource("batchId", batchId), Integer.class);
        assertThat(rowCount).isEqualTo(0);
    }

    private Integer resolve(Future<Integer> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID insertFixtureItem() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO item (code, name, classification, form, unit, created_by)
                        VALUES (:code, 'Test Item', 'TEST', 'TABLET', 'TABLET', (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource("code", "ITEM-" + UUID.randomUUID()), UUID.class);
    }

    private UUID insertFixtureStore() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO store (name, type, facility_id) VALUES ('Main Pharmacy', 'MAIN_PHARMACY', :facilityId)
                        RETURNING id
                        """,
                new MapSqlParameterSource("facilityId", BOOTSTRAP_FACILITY_ID), UUID.class);
    }

    private UUID insertFixtureBatch(UUID itemId, UUID storeId) {
        UUID supplierId = jdbcTemplate.queryForObject(
                "INSERT INTO supplier (code, name) VALUES (:code, 'Test Supplier') RETURNING id",
                new MapSqlParameterSource("code", "SUP-" + UUID.randomUUID().toString().substring(0, 8)),
                UUID.class);
        return jdbcTemplate.queryForObject("""
                        INSERT INTO stock_batch (item_id, store_id, batch_number, expiry_date, unit_cost_amount_minor,
                                                  supplier_id, delivery_note_ref, received_by)
                        VALUES (:itemId, :storeId, 'BATCH-1', CURRENT_DATE + INTERVAL '1 year', 1000, :supplierId,
                                'DN-1', (SELECT id FROM app_user WHERE username = 'admin'))
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("itemId", itemId)
                        .addValue("storeId", storeId)
                        .addValue("supplierId", supplierId),
                UUID.class);
    }
}
