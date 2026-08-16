package com.adags.modules.audit.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

// Keeps audit_log twelve months of real monthly partitions ahead of the
// current month, via the idempotent fn_ensure_audit_log_partition (V4), so
// the DEFAULT catch-all partition never actually receives a row.
@Component
public class AuditLogPartitionMaintenanceJob {

    private static final int MONTHS_AHEAD = 12;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    public AuditLogPartitionMaintenanceJob(NamedParameterJdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    // Runs at startup too — a fresh install must not depend on the monthly
    // cron having already fired once.
    @PostConstruct
    @Scheduled(cron = "0 0 3 1 * *")
    public void ensureUpcomingPartitionsExist() {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        for (int i = 0; i < MONTHS_AHEAD; i++) {
            LocalDate month = currentMonth.plusMonths(i).atDay(1);
            jdbcTemplate.queryForObject(
                    "SELECT fn_ensure_audit_log_partition(:month)",
                    new MapSqlParameterSource("month", month),
                    (rs, rowNum) -> null);
        }
    }
}
