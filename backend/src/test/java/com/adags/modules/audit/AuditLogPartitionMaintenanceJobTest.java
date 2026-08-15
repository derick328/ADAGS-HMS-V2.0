package com.adags.modules.audit;

import com.adags.modules.audit.infrastructure.AuditLogPartitionMaintenanceJob;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

// The job also runs once via @PostConstruct on every context load (all the
// other IntegrationTestSupport-based tests exercise that path implicitly).
// This proves it actually covers the twelve months it claims to.
class AuditLogPartitionMaintenanceJobTest extends IntegrationTestSupport {

    @Autowired
    private AuditLogPartitionMaintenanceJob job;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void createsPartitionsForTheNextTwelveMonths() {
        job.ensureUpcomingPartitionsExist();

        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        for (int i = 0; i < 12; i++) {
            String partitionName = "audit_log_" + currentMonth.plusMonths(i).toString().replace("-", "_");
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_class WHERE relname = :name",
                    new MapSqlParameterSource("name", partitionName), Integer.class);
            assertThat(count).as("partition %s should exist", partitionName).isEqualTo(1);
        }
    }
}
