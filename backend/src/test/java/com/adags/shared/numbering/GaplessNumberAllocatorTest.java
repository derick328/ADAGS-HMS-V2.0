package com.adags.shared.numbering;

import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GaplessNumberAllocatorTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private GaplessNumberAllocator allocator;

    @Test
    void formatsWithFacilityCodeYearAndZeroPaddedValue() {
        String sequenceName = "TEST_SEQ_" + UUID.randomUUID();
        String first = allocator.allocate(sequenceName, BOOTSTRAP_FACILITY_ID, 2026, 6);
        assertThat(first).matches(Pattern.quote("MAIN-2026-") + "\\d{6}");
    }

    @Test
    void isGaplessAndSequential() {
        String sequenceName = "TEST_SEQ_" + UUID.randomUUID();
        int year = Year.now().getValue();

        List<String> allocated = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> allocator.allocate(sequenceName, BOOTSTRAP_FACILITY_ID, year, 6))
                .collect(Collectors.toList());

        List<String> expected = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> String.format("MAIN-%d-%06d", year, i))
                .collect(Collectors.toList());

        assertThat(allocated).isEqualTo(expected);
    }

    // The concurrency case a UNIQUE constraint alone cannot prove: not "does
    // it reject a duplicate" but "does every concurrent caller get a distinct,
    // contiguous value with none skipped or reused" — the actual "gapless"
    // requirement in root CLAUDE.md "Numbering".
    @Test
    void concurrentAllocationsProduceAContiguousSetWithNoGapsOrDuplicates() throws Exception {
        String sequenceName = "TEST_SEQ_CONCURRENT_" + UUID.randomUUID();
        int year = Year.now().getValue();
        int callers = 20;

        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Callable<String>> tasks = IntStream.range(0, callers)
                    .<Callable<String>>mapToObj(i -> () -> allocator.allocate(sequenceName, BOOTSTRAP_FACILITY_ID, year, 6))
                    .collect(Collectors.toList());

            List<Future<String>> futures = pool.invokeAll(tasks);
            Set<Integer> values = futures.stream()
                    .map(f -> {
                        try {
                            String allocated = f.get();
                            return Integer.parseInt(allocated.substring(allocated.length() - 6));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toSet());

            assertThat(values).containsExactlyInAnyOrderElementsOf(
                    IntStream.rangeClosed(1, callers).boxed().collect(Collectors.toList()));
        } finally {
            pool.shutdown();
        }
    }
}
