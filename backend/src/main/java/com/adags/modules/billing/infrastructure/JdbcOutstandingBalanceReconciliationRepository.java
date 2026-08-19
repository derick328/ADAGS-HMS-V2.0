package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.BalanceDrift;
import com.adags.modules.billing.domain.OutstandingBalanceReconciliationRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// 02-billing-architecture.md §9.3: outstanding = total - payments_settled
// - credit_notes - write_offs - insurance_approved + refunds.
// insurance_approved is omitted from the recompute — always 0 today, no
// insurance module yet (same as every other component was, before write-
// off/credit-note/refund existed) — and will need to join in here the
// same way once it does.
@Repository
public class JdbcOutstandingBalanceReconciliationRepository implements OutstandingBalanceReconciliationRepository {

    private static final String RECOMPUTE_CTE = """
            WITH settled AS (
                SELECT il.invoice_id, SUM(pa.amount_minor) AS total
                FROM payment_allocation pa
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                JOIN payment p ON p.id = pa.payment_id
                WHERE p.status = 'SETTLED'
                GROUP BY il.invoice_id
            ), refunds AS (
                SELECT il.invoice_id, SUM(r.amount_minor) AS total
                FROM refund r
                JOIN payment_allocation pa ON pa.id = r.payment_allocation_id
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                WHERE r.status = 'APPLIED'
                GROUP BY il.invoice_id
            ), credits AS (
                SELECT invoice_id, SUM(amount_minor) AS total
                FROM credit_note WHERE status = 'APPLIED'
                GROUP BY invoice_id
            ), writeoffs AS (
                SELECT invoice_id, SUM(amount_minor) AS total
                FROM invoice_write_off WHERE status = 'APPLIED'
                GROUP BY invoice_id
            ), computed AS (
                SELECT i.id,
                       i.outstanding_total_minor AS stored,
                       i.gross_total_minor
                           - COALESCE(settled.total, 0)
                           + COALESCE(refunds.total, 0)
                           - COALESCE(credits.total, 0)
                           - COALESCE(writeoffs.total, 0) AS recomputed
                FROM invoice i
                LEFT JOIN settled ON settled.invoice_id = i.id
                LEFT JOIN refunds ON refunds.invoice_id = i.id
                LEFT JOIN credits ON credits.invoice_id = i.id
                LEFT JOIN writeoffs ON writeoffs.invoice_id = i.id
                WHERE i.state != 'DRAFT'
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcOutstandingBalanceReconciliationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<BalanceDrift> findDrifted() {
        String sql = RECOMPUTE_CTE + """
                SELECT id, stored, recomputed FROM computed WHERE stored != recomputed
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(),
                (rs, rowNum) -> new BalanceDrift((UUID) rs.getObject("id"), rs.getLong("stored"), rs.getLong("recomputed")));
    }

    @Override
    public void correct(UUID invoiceId, long recomputedOutstandingMinor) {
        jdbcTemplate.update("UPDATE invoice SET outstanding_total_minor = :recomputed WHERE id = :id",
                new MapSqlParameterSource().addValue("id", invoiceId).addValue("recomputed", recomputedOutstandingMinor));
    }
}
