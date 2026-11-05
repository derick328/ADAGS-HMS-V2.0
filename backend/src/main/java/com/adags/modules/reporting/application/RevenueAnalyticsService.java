package com.adags.modules.reporting.application;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 18-admin.md / 02-billing-architecture.md §13. A pure read model over the
// existing ledger — no new financial table, no new column, nothing cached.
// Every aggregate runs in Postgres; nothing here pulls invoice rows into
// the JVM to add them up.
//
// Three predicates are repeated deliberately rather than abstracted, because
// each encodes an accounting rule that must stay visible at its use site:
//
//   i.state NOT IN ('DRAFT','VOID')   billed. A DRAFT invoice was never
//                                     issued; a VOID one was cancelled and
//                                     was never earned.
//   p.status = 'SETTLED'              collected. A cheque sitting in
//                                     PENDING_CLEARANCE is not money.
//   pa.amount_minor - pa.refunded_minor
//                                     collected, net of refunds. An
//                                     approved refund increments
//                                     refunded_minor, so this one
//                                     expression handles reversals.
//
// Why collected needs no VOID filter to be correct: InvoiceRepository
// .voidUnpaid only moves an invoice to VOID when nothing has been paid
// against it (outstanding == gross), enforced in its own WHERE clause. A
// VOID invoice therefore has no settled allocations by construction. The
// filter is still written out for the reader; it cannot change a number,
// which is also why these figures reconcile with
// RevenueReportService.collectedCashMinor rather than quietly disagreeing
// with the headline.
@Service
public class RevenueAnalyticsService {

    /** A line chart nobody can read, and an unbounded response, both start here. */
    private static final int MAX_TREND_BUCKETS = 400;

    /** Top-N lists are for ranking, not for exporting the catalogue. */
    private static final int MAX_TOP_ITEMS = 50;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RevenueAnalyticsService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------------- summary

    public RevenueSummary summary(LocalDate from, LocalDate to, UUID facilityId) {
        MapSqlParameterSource params = periodParams(from, to, facilityId);

        Map<String, Object> billed = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(il.gross_minor), 0)             AS gross_minor,
                       COALESCE(SUM(il.patient_portion_minor), 0)   AS patient_minor
                FROM invoice_line il
                JOIN invoice i ON i.id = il.invoice_id
                WHERE i.facility_id = :facilityId
                  AND i.state NOT IN ('DRAFT', 'VOID')
                  AND il.created_at::date BETWEEN :from AND :to
                """, params);

        // Insurance is billed when the claim is raised. One claim row per
        // invoice (resubmission transitions the existing row), so EXISTS
        // cannot fan out the line sum the way a JOIN would.
        long insuranceBilledMinor = scalar("""
                SELECT COALESCE(SUM(il.insurance_portion_minor), 0)
                FROM invoice_line il
                JOIN invoice i ON i.id = il.invoice_id
                WHERE i.facility_id = :facilityId
                  AND i.state NOT IN ('DRAFT', 'VOID')
                  AND il.created_at::date BETWEEN :from AND :to
                  AND EXISTS (SELECT 1 FROM insurance_claim c WHERE c.invoice_id = i.id)
                """, params);

        Map<String, Object> collected = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(pa.amount_minor - pa.refunded_minor), 0) AS net_minor,
                       COALESCE(SUM(pa.refunded_minor), 0)                   AS refunded_minor
                FROM payment_allocation pa
                JOIN payment p      ON p.id = pa.payment_id
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                JOIN invoice i       ON i.id = il.invoice_id
                WHERE p.status = 'SETTLED'
                  AND p.received_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                  AND i.state <> 'VOID'
                """, params);

        // Point-in-time, not period-bounded — same two buckets
        // ReceivablesOverviewService reports, so the KPI row and the
        // receivables strip can never disagree.
        long postDischarge = scalar("""
                SELECT COALESCE(SUM(outstanding_total_minor), 0) FROM invoice
                WHERE state = 'RECEIVABLE_POST_DISCHARGE' AND facility_id = :facilityId
                """, params);
        long activeEncounter = scalar("""
                SELECT COALESCE(SUM(outstanding_total_minor), 0) FROM invoice
                WHERE state IN ('ISSUED', 'PATIENT_PORTION_SETTLED')
                  AND outstanding_total_minor > 0 AND facility_id = :facilityId
                """, params);

        // Settled = nothing outstanding. A partially paid invoice fails
        // both halves of this predicate and is not counted.
        Map<String, Object> invoiceCounts = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS issued_count,
                       COUNT(*) FILTER (
                           WHERE i.outstanding_total_minor = 0
                             AND i.state IN ('PATIENT_PORTION_SETTLED', 'CLOSED')
                       ) AS settled_count
                FROM invoice i
                WHERE i.facility_id = :facilityId
                  AND i.state NOT IN ('DRAFT', 'VOID')
                  AND i.created_at::date BETWEEN :from AND :to
                """, params);

        // The collection rate's cohort: the patient portion billed in this
        // period, and how much of exactly those lines has been settled to
        // date. One set of lines, two measures over it — a ratio that
        // reconciles, unlike collected-in-period over billed-in-period.
        Map<String, Object> cohort = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(il.patient_portion_minor), 0) AS billed_minor,
                       COALESCE(SUM(LEAST(il.patient_portion_minor,
                                          COALESCE(s.settled_minor, 0))), 0) AS settled_minor
                FROM invoice_line il
                JOIN invoice i ON i.id = il.invoice_id
                LEFT JOIN (%s) s ON s.invoice_line_id = il.id
                WHERE i.facility_id = :facilityId
                  AND i.state NOT IN ('DRAFT', 'VOID')
                  AND il.created_at::date BETWEEN :from AND :to
                """.formatted(SETTLED_PER_LINE), params);

        return new RevenueSummary(
                from, to,
                longOf(collected.get("net_minor")),
                longOf(billed.get("gross_minor")),
                longOf(billed.get("patient_minor")),
                insuranceBilledMinor,
                longOf(collected.get("refunded_minor")),
                postDischarge + activeEncounter,
                postDischarge,
                activeEncounter,
                intOf(invoiceCounts.get("settled_count")),
                intOf(invoiceCounts.get("issued_count")),
                longOf(cohort.get("billed_minor")),
                longOf(cohort.get("settled_minor")),
                "TZS");
    }

    // ------------------------------------------------------- adjustments

    /**
     * Credit notes and write-offs applied in the period, plus the refunds
     * already netted out of collected cash. Invoice-level and deliberately
     * unattributed to any category — see FinancialAdjustments for why
     * inventing a line split would be worse than leaving the total whole.
     */
    public FinancialAdjustments adjustments(LocalDate from, LocalDate to, UUID facilityId) {
        MapSqlParameterSource params = periodParams(from, to, facilityId);

        // APPLIED only, dated by approval: that is when outstanding moved.
        Map<String, Object> creditNotes = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(cn.amount_minor), 0) AS total_minor,
                       COUNT(*)                          AS applied_count
                FROM credit_note cn
                JOIN invoice i ON i.id = cn.invoice_id
                WHERE cn.status = 'APPLIED'
                  AND cn.approved_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                """, params);

        Map<String, Object> writeOffs = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(w.amount_minor), 0) AS total_minor,
                       COUNT(*)                         AS applied_count
                FROM invoice_write_off w
                JOIN invoice i ON i.id = w.invoice_id
                WHERE w.status = 'APPLIED'
                  AND w.decided_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                """, params);

        // Refunds are already deducted from collectedCashMinor via
        // payment_allocation.refunded_minor; this is the same money shown
        // on its own so the gross/net gap is visible rather than implied.
        long refundsMinor = scalar("""
                SELECT COALESCE(SUM(pa.refunded_minor), 0)
                FROM payment_allocation pa
                JOIN payment p       ON p.id = pa.payment_id
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                JOIN invoice i       ON i.id = il.invoice_id
                WHERE p.status = 'SETTLED'
                  AND p.received_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                """, params);

        // Point-in-time, not period-bounded: what is still awaiting a
        // decision now, whenever it was raised.
        int pendingCreditNotes = (int) scalar("""
                SELECT COUNT(*) FROM credit_note cn JOIN invoice i ON i.id = cn.invoice_id
                WHERE cn.status = 'PENDING' AND i.facility_id = :facilityId
                """, params);
        int pendingWriteOffs = (int) scalar("""
                SELECT COUNT(*) FROM invoice_write_off w JOIN invoice i ON i.id = w.invoice_id
                WHERE w.status = 'PENDING' AND i.facility_id = :facilityId
                """, params);

        return new FinancialAdjustments(from, to,
                longOf(creditNotes.get("total_minor")), intOf(creditNotes.get("applied_count")),
                longOf(writeOffs.get("total_minor")), intOf(writeOffs.get("applied_count")),
                refundsMinor, pendingCreditNotes, pendingWriteOffs, "TZS");
    }

    // --------------------------------------------------------- by category

    public List<CategoryRevenueLine> byCategory(LocalDate from, LocalDate to, UUID facilityId) {
        MapSqlParameterSource params = periodParams(from, to, facilityId);

        Map<RevenueCategory, long[]> billed = new EnumMap<>(RevenueCategory.class);
        jdbcTemplate.query("""
                SELECT %s AS category,
                       COALESCE(SUM(il.gross_minor), 0) AS billed_minor,
                       COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM insurance_claim c WHERE c.invoice_id = i.id)
                                         THEN il.insurance_portion_minor ELSE 0 END), 0) AS insurance_minor,
                       COUNT(*) AS line_count
                FROM invoice_line il
                JOIN invoice i      ON i.id = il.invoice_id
                JOIN service_item si ON si.id = il.service_item_id
                WHERE i.facility_id = :facilityId
                  AND i.state NOT IN ('DRAFT', 'VOID')
                  AND il.created_at::date BETWEEN :from AND :to
                GROUP BY 1
                """.formatted(RevenueCategory.CASE_SQL), params, rs -> {
            billed.put(RevenueCategory.valueOf(rs.getString("category")), new long[]{
                    rs.getLong("billed_minor"), rs.getLong("insurance_minor"), rs.getLong("line_count")});
        });

        Map<RevenueCategory, Long> collected = new EnumMap<>(RevenueCategory.class);
        jdbcTemplate.query("""
                SELECT %s AS category,
                       COALESCE(SUM(pa.amount_minor - pa.refunded_minor), 0) AS collected_minor
                FROM payment_allocation pa
                JOIN payment p        ON p.id = pa.payment_id
                JOIN invoice_line il  ON il.id = pa.invoice_line_id
                JOIN invoice i        ON i.id = il.invoice_id
                JOIN service_item si  ON si.id = il.service_item_id
                WHERE p.status = 'SETTLED'
                  AND p.received_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                  AND i.state <> 'VOID'
                GROUP BY 1
                """.formatted(RevenueCategory.CASE_SQL), params, rs -> {
            collected.put(RevenueCategory.valueOf(rs.getString("category")), rs.getLong("collected_minor"));
        });

        // Fixed display order, zero rows included: a category that earned
        // nothing this period is a finding, not a row to hide.
        List<CategoryRevenueLine> lines = new ArrayList<>();
        for (RevenueCategory category : RevenueCategory.displayOrder()) {
            long[] b = billed.getOrDefault(category, new long[]{0, 0, 0});
            lines.add(new CategoryRevenueLine(category, category.displayName(),
                    b[0], collected.getOrDefault(category, 0L), b[1], (int) b[2], "TZS"));
        }
        return lines;
    }

    // -------------------------------------------------------------- trend

    public List<RevenueTrendPoint> trend(LocalDate from, LocalDate to, UUID facilityId, TrendGranularity granularity) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        int buckets = granularity.bucketCount(from, to);
        if (buckets > MAX_TREND_BUCKETS) {
            throw new IllegalArgumentException(
                    "Range of %d %s buckets exceeds the %d-point limit — widen the granularity"
                            .formatted(buckets, granularity.name().toLowerCase(), MAX_TREND_BUCKETS));
        }

        MapSqlParameterSource params = periodParams(from, to, facilityId)
                .addValue("granularity", granularity.sqlUnit());

        Map<LocalDate, long[]> byBucket = new LinkedHashMap<>();
        for (LocalDate bucket : granularity.buckets(from, to)) {
            byBucket.put(bucket, new long[]{0, 0});
        }

        jdbcTemplate.query("""
                SELECT date_trunc(:granularity, il.created_at)::date AS bucket_start,
                       COALESCE(SUM(il.gross_minor), 0) AS billed_minor
                FROM invoice_line il
                JOIN invoice i ON i.id = il.invoice_id
                WHERE i.facility_id = :facilityId
                  AND i.state NOT IN ('DRAFT', 'VOID')
                  AND il.created_at::date BETWEEN :from AND :to
                GROUP BY 1
                """, params, rs -> {
            long[] slot = byBucket.get(rs.getObject("bucket_start", LocalDate.class));
            if (slot != null) {
                slot[0] = rs.getLong("billed_minor");
            }
        });

        jdbcTemplate.query("""
                SELECT date_trunc(:granularity, p.received_at)::date AS bucket_start,
                       COALESCE(SUM(pa.amount_minor - pa.refunded_minor), 0) AS collected_minor
                FROM payment_allocation pa
                JOIN payment p       ON p.id = pa.payment_id
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                JOIN invoice i       ON i.id = il.invoice_id
                WHERE p.status = 'SETTLED'
                  AND p.received_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                  AND i.state <> 'VOID'
                GROUP BY 1
                """, params, rs -> {
            long[] slot = byBucket.get(rs.getObject("bucket_start", LocalDate.class));
            if (slot != null) {
                slot[1] = rs.getLong("collected_minor");
            }
        });

        return byBucket.entrySet().stream()
                .map(e -> new RevenueTrendPoint(e.getKey(), e.getValue()[0], e.getValue()[1], "TZS"))
                .toList();
    }

    // ---------------------------------------------------------- top items

    public List<TopServiceItemLine> topItems(LocalDate from, LocalDate to, UUID facilityId,
                                             RevenueCategory category, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), MAX_TOP_ITEMS);
        MapSqlParameterSource params = periodParams(from, to, facilityId)
                .addValue("category", category.name());

        // strength/form: a code correspondence to the stock catalogue, not
        // an FK (see TopServiceItemLine). item.code and service_item.code
        // are both UNIQUE, so this cannot multiply a row.
        Map<UUID, Object[]> rows = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT si.id, si.code, si.name, it.strength, it.form,
                       COALESCE(SUM(il.quantity), 0)  AS quantity_billed,
                       COUNT(*)                       AS billed_lines,
                       COUNT(*) FILTER (WHERE il.patient_portion_minor
                                              - COALESCE(s.settled_minor, 0) <= 0) AS settled_lines,
                       COALESCE(SUM(il.gross_minor), 0) AS billed_minor
                FROM invoice_line il
                JOIN invoice i       ON i.id = il.invoice_id
                JOIN service_item si ON si.id = il.service_item_id
                LEFT JOIN item it    ON it.code = si.code
                LEFT JOIN (%s) s     ON s.invoice_line_id = il.id
                WHERE i.facility_id = :facilityId
                  AND i.state NOT IN ('DRAFT', 'VOID')
                  AND il.created_at::date BETWEEN :from AND :to
                  AND %s = :category
                GROUP BY si.id, si.code, si.name, it.strength, it.form
                """.formatted(SETTLED_PER_LINE, RevenueCategory.CASE_SQL), params, rs -> {
            rows.put((UUID) rs.getObject("id"), new Object[]{
                    rs.getString("code"), rs.getString("name"), rs.getString("strength"), rs.getString("form"),
                    rs.getLong("quantity_billed"), rs.getInt("billed_lines"), rs.getInt("settled_lines"),
                    rs.getLong("billed_minor"), 0L});
        });

        // Collections in the period, on the same basis as the category
        // chart — so this list's collected column sums into that chart's
        // bar for this category. An item paid for this period but billed
        // before it appears here with billed 0, which is accurate, not a gap.
        jdbcTemplate.query("""
                SELECT si.id, si.code, si.name, it.strength, it.form,
                       COALESCE(SUM(pa.amount_minor - pa.refunded_minor), 0) AS collected_minor
                FROM payment_allocation pa
                JOIN payment p       ON p.id = pa.payment_id
                JOIN invoice_line il ON il.id = pa.invoice_line_id
                JOIN invoice i       ON i.id = il.invoice_id
                JOIN service_item si ON si.id = il.service_item_id
                LEFT JOIN item it    ON it.code = si.code
                WHERE p.status = 'SETTLED'
                  AND p.received_at::date BETWEEN :from AND :to
                  AND i.facility_id = :facilityId
                  AND i.state <> 'VOID'
                  AND %s = :category
                GROUP BY si.id, si.code, si.name, it.strength, it.form
                """.formatted(RevenueCategory.CASE_SQL), params, rs -> {
            UUID id = (UUID) rs.getObject("id");
            Object[] existing = rows.get(id);
            if (existing != null) {
                existing[8] = rs.getLong("collected_minor");
            } else {
                rows.put(id, new Object[]{
                        rs.getString("code"), rs.getString("name"), rs.getString("strength"), rs.getString("form"),
                        0L, 0, 0, 0L, rs.getLong("collected_minor")});
            }
        });

        return rows.entrySet().stream()
                .map(e -> {
                    Object[] r = e.getValue();
                    return new TopServiceItemLine(e.getKey(), (String) r[0], (String) r[1], (String) r[2],
                            (String) r[3], (Long) r[4], (Integer) r[5], (Integer) r[6], (Long) r[7], (Long) r[8],
                            "TZS");
                })
                // Collected first — the question is "what earned the most
                // money", and money in the bank outranks money invoiced.
                .sorted((a, b) -> {
                    int byCollected = Long.compare(b.collectedMinor(), a.collectedMinor());
                    if (byCollected != 0) return byCollected;
                    int byBilled = Long.compare(b.billedMinor(), a.billedMinor());
                    return byBilled != 0 ? byBilled : a.name().compareToIgnoreCase(b.name());
                })
                .filter(line -> line.billedMinor() != 0 || line.collectedMinor() != 0)
                .limit(limit)
                .toList();
    }

    // ------------------------------------------------------------- helpers

    /** Net settled amount per invoice line, as of now. Refunds already deducted. */
    private static final String SETTLED_PER_LINE = """
            SELECT pa2.invoice_line_id,
                   SUM(pa2.amount_minor - pa2.refunded_minor) AS settled_minor
            FROM payment_allocation pa2
            JOIN payment p2 ON p2.id = pa2.payment_id
            WHERE p2.status = 'SETTLED'
            GROUP BY pa2.invoice_line_id""";

    private MapSqlParameterSource periodParams(LocalDate from, LocalDate to, UUID facilityId) {
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("facilityId", facilityId);
    }

    private long scalar(String sql, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private static long longOf(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static int intOf(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
