package com.adags.modules.finance;

import com.adags.modules.finance.application.GeneratePayrollBatchService;
import com.adags.modules.finance.domain.DuplicateBatchException;
import com.adags.modules.finance.domain.EmploymentType;
import com.adags.modules.finance.domain.SalaryDisbursementBatch;
import com.adags.modules.finance.domain.Staff;
import com.adags.modules.finance.domain.StaffRepository;
import com.adags.shared.kernel.Money;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// root CLAUDE.md "Concurrency": UNIQUE(batch_type, month, year) on
// salary_disbursement_batch is the real guard — "running twice for one
// month must not pay twice" (18-admin.md §5.1). A read-then-write check
// in GeneratePayrollBatchService would lose this race; the unique index
// is what actually decides it. Fires two concurrent generate() calls for
// the same period and proves exactly one succeeds.
class PayrollBatchConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private GeneratePayrollBatchService generatePayrollBatchService;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE salary_record, salary_disbursement_batch, staff", new MapSqlParameterSource());
    }

    @Test
    void concurrentGenerateCallsForTheSamePeriodLeaveExactlyOneBatch() throws Exception {
        UUID admin = bootstrapAdminId();
        staffRepository.insert(new Staff(null, null, "Test Staff " + UUID.randomUUID(), EmploymentType.PERMANENT,
                new Money(1_000_000, "TZS"), LocalDate.of(2020, 1, 1), true, null));

        int racers = 2;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<SalaryDisbursementBatch>> futures = List.of(
                    pool.submit(() -> raceGenerate(admin, ready, go)),
                    pool.submit(() -> raceGenerate(admin, ready, go)));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long successes = futures.stream().map(this::resolve).filter(r -> r != null).count();
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        Integer batchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM salary_disbursement_batch WHERE month = 8 AND year = 2026",
                new MapSqlParameterSource(), Integer.class);
        assertThat(batchCount).isEqualTo(1);
    }

    private SalaryDisbursementBatch raceGenerate(UUID admin, CountDownLatch ready, CountDownLatch go) throws InterruptedException {
        ready.countDown();
        go.await();
        try {
            return generatePayrollBatchService.generate(8, 2026, admin);
        } catch (DuplicateBatchException ex) {
            return null;
        }
    }

    private SalaryDisbursementBatch resolve(Future<SalaryDisbursementBatch> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
