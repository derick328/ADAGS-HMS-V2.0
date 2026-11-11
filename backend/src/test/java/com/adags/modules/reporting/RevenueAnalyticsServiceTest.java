package com.adags.modules.reporting;

import com.adags.modules.billing.application.ChargePostingCommand;
import com.adags.modules.billing.application.ChargePostingResult;
import com.adags.modules.billing.application.ChargePostingService;
import com.adags.modules.billing.application.OpenCashierShiftService;
import com.adags.modules.billing.application.PostPaymentCommand;
import com.adags.modules.billing.application.PostPaymentService;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PaymentAllocationRejectedException;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentBypassSource;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.reporting.application.CategoryRevenueLine;
import com.adags.modules.reporting.application.FinancialAdjustments;
import com.adags.modules.reporting.application.RevenueAnalyticsService;
import com.adags.modules.reporting.application.RevenueCategory;
import com.adags.modules.reporting.application.RevenueSummary;
import com.adags.modules.reporting.application.RevenueTrendPoint;
import com.adags.modules.reporting.application.TopServiceItemLine;
import com.adags.modules.reporting.application.TrendGranularity;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Every claim the /admin and /admin/revenue screens make about money, against
// real Postgres. The point of each test is a specific way these reports could
// lie: counting a voided invoice as earned, counting a partial payment as
// settled, counting the same line in two categories, counting a refund twice,
// or ranking a medicine by stock movement instead of by billing.
//
// Never truncates service_item / price_list / price_list_entry / department —
// backend/CLAUDE.md's TRUNCATE CASCADE lesson. Fixture service items are
// inserted per test and left behind; they carry a unique code and only ever
// appear in a period a test explicitly queries.
class RevenueAnalyticsServiceTest extends IntegrationTestSupport {

    private static final UUID BOOTSTRAP_FACILITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Autowired
    private RevenueAnalyticsService analytics;
    @Autowired
    private ChargePostingService chargePostingService;
    @Autowired
    private PostPaymentService postPaymentService;
    @Autowired
    private OpenCashierShiftService openCashierShiftService;
    @Autowired
    private PaymentBypassRepository paymentBypassRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                        TRUNCATE insurance_claim, scheme_coverage_rule, insurance_policy, insurance_scheme,
                                 payment_allocation, payment, cashier_shift, service_authorization, invoice_line,
                                 invoice, payment_bypass, outbox_event, encounter, patient CASCADE
                        """,
                new MapSqlParameterSource());
    }

    // ------------------------------------------------------------- summary

    @Test
    void emptyLedgerReportsZerosRatherThanNulls() {
        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.collectedCashMinor()).isZero();
        assertThat(summary.grossBilledMinor()).isZero();
        assertThat(summary.patientPortionBilledMinor()).isZero();
        assertThat(summary.insuranceBilledMinor()).isZero();
        assertThat(summary.outstandingTotalMinor()).isZero();
        assertThat(summary.settledInvoiceCount()).isZero();
        assertThat(summary.cohortPatientPortionBilledMinor()).isZero();
        assertThat(summary.currencyCode()).isEqualTo("TZS");
    }

    // Partial payment is only legal under an active PaymentBypass
    // (PostPaymentService.validateAllocations enforces exact payment
    // otherwise). Every partially-paid fixture below therefore grants one —
    // the rule is the reason the state exists at all, not an obstacle to
    // work around.
    @Test
    void billedAndCollectedAreDistinctFiguresNeverTheSameNumber() {
        Fixture f = fixture();
        grantBypass(f);
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 15_000);

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.grossBilledMinor()).isEqualTo(40_000);
        assertThat(summary.patientPortionBilledMinor()).isEqualTo(40_000);
        assertThat(summary.collectedCashMinor()).isEqualTo(15_000);
    }

    // A partial payment is the single most common way a collection report
    // overstates itself.
    @Test
    void partiallyPaidInvoiceIsNotCountedAsSettled() {
        Fixture f = fixture();
        grantBypass(f);
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 15_000);

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.issuedInvoiceCount()).isEqualTo(1);
        assertThat(summary.settledInvoiceCount()).isZero();
        assertThat(summary.cohortPatientPortionBilledMinor()).isEqualTo(40_000);
        assertThat(summary.cohortSettledMinor()).isEqualTo(15_000);
    }

    @Test
    void fullyPaidInvoiceCountsAsSettledOnce() {
        Fixture f = fixture();
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 40_000);

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.settledInvoiceCount()).isEqualTo(1);
        assertThat(summary.cohortSettledMinor()).isEqualTo(40_000);
    }

    @Test
    void refundReducesCollectedAndIsReportedSeparately() {
        Fixture f = fixture();
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 40_000);
        refundAllocation(line, 10_000);

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.collectedCashMinor()).isEqualTo(30_000);
        assertThat(summary.refundedMinor()).isEqualTo(10_000);
        // The refund did not retroactively un-bill the service.
        assertThat(summary.grossBilledMinor()).isEqualTo(40_000);
    }

    @Test
    void voidedInvoiceIsExcludedFromBilled() {
        Fixture f = fixture();
        postCharge(f, consultItem(40_000), 1);
        jdbcTemplate.update("""
                UPDATE invoice SET state = 'VOID', void_reason = 'test', voided_at = now(),
                                   voided_by = (SELECT id FROM app_user WHERE username = 'admin')
                """, new MapSqlParameterSource());

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.grossBilledMinor()).isZero();
        assertThat(summary.issuedInvoiceCount()).isZero();
    }

    @Test
    void chargesAndPaymentsOutsideThePeriodAreExcluded() {
        Fixture f = fixture();
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 40_000);
        jdbcTemplate.update("UPDATE invoice_line SET created_at = now() - interval '40 days'",
                new MapSqlParameterSource());
        jdbcTemplate.update("UPDATE payment SET received_at = now() - interval '40 days'",
                new MapSqlParameterSource());

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.grossBilledMinor()).isZero();
        assertThat(summary.collectedCashMinor()).isZero();
    }

    @Test
    void anotherFacilitysMoneyIsExcluded() {
        UUID otherFacility = jdbcTemplate.queryForObject(
                "INSERT INTO facility (code, name) VALUES (:code, 'Other') RETURNING id",
                new MapSqlParameterSource("code", "OTHER-" + UUID.randomUUID().toString().substring(0, 8)), UUID.class);
        Fixture f = fixtureAt(otherFacility);
        UUID line = chargePostingService.postCharge(new ChargePostingCommand(
                f.encounterId, f.patientId, otherFacility, PayerCategory.CASH, null,
                consultItem(40_000), 1, "TestFixture", UUID.randomUUID(), f.admin)).invoiceLineId();
        openCashierShiftService.open(otherFacility, f.admin);
        postPaymentService.recordPayment(new PostPaymentCommand(f.patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(line, 40_000)), f.admin));

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.grossBilledMinor()).isZero();
        assertThat(summary.collectedCashMinor()).isZero();
    }

    // -------------------------------------------------------- by category

    @Test
    void everyCategoryIsReturnedInFixedOrderEvenWhenItEarnedNothing() {
        List<CategoryRevenueLine> lines = analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(lines).extracting(CategoryRevenueLine::category)
                .containsExactlyElementsOf(RevenueCategory.displayOrder());
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.billedMinor()).isZero();
            assertThat(line.collectedMinor()).isZero();
        });
    }

    @Test
    void eachCategoryGetsItsOwnChargeAndNoneIsCountedTwice() {
        Fixture f = fixture();
        postCharge(f, consultItem(10_000), 1);
        postCharge(f, item("LAB", null, 20_000), 1);
        postCharge(f, item("PHARMACY", "PHARMACY", 30_000), 1);
        postCharge(f, item("PROCEDURE", "SURGERY", 40_000), 1);
        postCharge(f, item("PROCEDURE", "PROCEDURE", 50_000), 1);
        postCharge(f, item("WARD", null, 60_000), 1);

        List<CategoryRevenueLine> lines = analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);
        Map<RevenueCategory, Long> billed = billedByCategory(lines);

        assertThat(billed.get(RevenueCategory.CONSULTATION)).isEqualTo(10_000);
        assertThat(billed.get(RevenueCategory.LABORATORY)).isEqualTo(20_000);
        assertThat(billed.get(RevenueCategory.PHARMACY)).isEqualTo(30_000);
        assertThat(billed.get(RevenueCategory.SURGERY)).isEqualTo(40_000);
        assertThat(billed.get(RevenueCategory.THEATRE_PROCEDURE)).isEqualTo(50_000);
        assertThat(billed.get(RevenueCategory.INPATIENT)).isEqualTo(60_000);
        assertThat(billed.get(RevenueCategory.OTHER)).isZero();

        // The whole point of a non-overlapping mapping: the bars sum to the
        // period total, with nothing double-counted and nothing dropped.
        assertThat(billed.values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID).grossBilledMinor())
                .isEqualTo(210_000);
    }

    // Radiology keys on the catalogue's own type, not on billing_category
    // and not on "it is a PROCEDURE". Imaging must leave the OTHER bucket
    // without taking surgery or laboratory with it.
    @Test
    void radiologyIsItsOwnCategoryAndTakesNothingFromSurgeryOrLab() {
        Fixture f = fixture();
        postCharge(f, item("PROCEDURE", "RADIOLOGY", 25_000), 1);
        postCharge(f, item("PROCEDURE", "SURGERY", 400_000), 1);
        postCharge(f, item("LAB", null, 15_000), 1);

        Map<RevenueCategory, Long> billed =
                billedByCategory(analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID));

        assertThat(billed.get(RevenueCategory.RADIOLOGY)).isEqualTo(25_000);
        assertThat(billed.get(RevenueCategory.SURGERY)).isEqualTo(400_000);
        assertThat(billed.get(RevenueCategory.LABORATORY)).isEqualTo(15_000);
        assertThat(billed.get(RevenueCategory.OTHER)).isZero();
    }

    // DIAGNOSTIC_EXAMINATIONS covers both imaging and laboratory in the real
    // catalogue, so a mapping keyed on billing_category would claim lab
    // revenue as radiology. This proves the mapping ignores it.
    @Test
    void laboratoryBilledUnderDiagnosticExaminationsIsNotCountedAsRadiology() {
        Fixture f = fixture();
        postCharge(f, itemWithBillingCategory("LAB", null, "DIAGNOSTIC_EXAMINATIONS", 15_000), 1);

        Map<RevenueCategory, Long> billed =
                billedByCategory(analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID));

        assertThat(billed.get(RevenueCategory.LABORATORY)).isEqualTo(15_000);
        assertThat(billed.get(RevenueCategory.RADIOLOGY)).isZero();
    }

    // A service_item whose type falls outside the mapped set must land in
    // OTHER. Dropping it would make the category chart quietly disagree
    // with the headline total.
    @Test
    void unmappedServiceTypeFallsToOtherRatherThanDisappearing() {
        Fixture f = fixture();
        postCharge(f, item("PROCEDURE", "OPTICAL", 25_000), 1);
        postCharge(f, item("PROCEDURE", null, 5_000), 1);

        List<CategoryRevenueLine> lines = analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(billedByCategory(lines).get(RevenueCategory.OTHER)).isEqualTo(30_000);
        assertThat(lines.stream().mapToLong(CategoryRevenueLine::billedMinor).sum())
                .isEqualTo(analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID).grossBilledMinor());
    }

    @Test
    void everyCatalogueTypeInUseLandsInExactlyOneCategory() {
        Fixture f = fixture();
        List<String> types = List.of("SURGERY", "PROCEDURE", "RADIOLOGY", "OPTICAL", "DIALYSIS",
                "OTHERS", "OTHER_GENERAL_CHARGES");
        for (String type : types) {
            postCharge(f, item("PROCEDURE", type, 10_000), 1);
        }

        List<CategoryRevenueLine> lines = analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        // One charge each, nothing counted twice, nothing dropped.
        assertThat(lines.stream().mapToInt(CategoryRevenueLine::billedLineCount).sum()).isEqualTo(types.size());
        assertThat(lines.stream().mapToLong(CategoryRevenueLine::billedMinor).sum())
                .isEqualTo(10_000L * types.size())
                .isEqualTo(analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID).grossBilledMinor());
    }

    @Test
    void collectionsAreAttributedToTheCategoryOfTheLineTheyPaidFor() {
        Fixture f = fixture();
        UUID labLine = postCharge(f, item("LAB", null, 20_000), 1);
        postCharge(f, item("PHARMACY", "PHARMACY", 30_000), 1);
        pay(f, labLine, 20_000);

        Map<RevenueCategory, Long> collected = analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID)
                .stream().collect(java.util.stream.Collectors.toMap(
                        CategoryRevenueLine::category, CategoryRevenueLine::collectedMinor));

        assertThat(collected.get(RevenueCategory.LABORATORY)).isEqualTo(20_000);
        assertThat(collected.get(RevenueCategory.PHARMACY)).isZero();
    }

    @Test
    void categoryCollectionsSumToTheSummaryCollectedTotal() {
        Fixture f = fixture();
        grantBypass(f);
        UUID a = postCharge(f, item("LAB", null, 20_000), 1);
        UUID b = postCharge(f, item("PHARMACY", "PHARMACY", 30_000), 1);
        pay(f, a, 20_000);
        pay(f, b, 12_000);

        long categorySum = analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID)
                .stream().mapToLong(CategoryRevenueLine::collectedMinor).sum();

        assertThat(categorySum)
                .isEqualTo(analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID).collectedCashMinor())
                .isEqualTo(32_000);
    }

    // ---------------------------------------------------------- top items

    @Test
    void medicinesAreRankedByRevenueHighestFirst() {
        Fixture f = fixture();
        String cheap = namedItem("PHARMACY", "PHARMACY", 5_000, "Paracetamol 500mg tab");
        String dear = namedItem("PHARMACY", "PHARMACY", 80_000, "Meropenem 1g inj");
        UUID cheapLine = postCharge(f, cheap, 2);
        UUID dearLine = postCharge(f, dear, 1);
        pay(f, cheapLine, 10_000);
        pay(f, dearLine, 80_000);

        List<TopServiceItemLine> top = analytics.topItems(
                YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.PHARMACY, 5);

        assertThat(top).extracting(TopServiceItemLine::name)
                .containsExactly("Meropenem 1g inj", "Paracetamol 500mg tab");
        assertThat(top.get(0).collectedMinor()).isEqualTo(80_000);
        assertThat(top.get(1).quantityBilled()).isEqualTo(2);
        assertThat(top.get(1).billedMinor()).isEqualTo(10_000);
    }

    @Test
    void labTestsReportBilledLinesAndSettledLinesSeparately() {
        Fixture f = fixture();
        String test = namedItem("LAB", null, 15_000, "Full blood picture");
        UUID paid = postCharge(f, test, 1);
        postCharge(f, test, 1);
        pay(f, paid, 15_000);

        List<TopServiceItemLine> top = analytics.topItems(
                YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.LABORATORY, 5);

        assertThat(top).hasSize(1);
        assertThat(top.get(0).billedLineCount()).isEqualTo(2);
        assertThat(top.get(0).settledLineCount()).isEqualTo(1);
        assertThat(top.get(0).billedMinor()).isEqualTo(30_000);
        assertThat(top.get(0).collectedMinor()).isEqualTo(15_000);
    }

    // An unpaid order is billed, never collected. This is the line between
    // "revenue" and "wishful thinking".
    @Test
    void unpaidLabOrderContributesBilledButNotCollected() {
        Fixture f = fixture();
        postCharge(f, namedItem("LAB", null, 15_000, "Malaria rapid test"), 1);

        TopServiceItemLine line = analytics.topItems(
                YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.LABORATORY, 5).get(0);

        assertThat(line.billedMinor()).isEqualTo(15_000);
        assertThat(line.collectedMinor()).isZero();
        assertThat(line.settledLineCount()).isZero();
    }

    @Test
    void proceduresAreRankedWithinTheirOwnCategoryOnly() {
        Fixture f = fixture();
        postCharge(f, namedItem("PROCEDURE", "SURGERY", 300_000, "Caesarean section"), 1);
        postCharge(f, namedItem("PROCEDURE", "PROCEDURE", 40_000, "Wound suturing"), 1);

        List<TopServiceItemLine> surgery = analytics.topItems(
                YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.SURGERY, 5);
        List<TopServiceItemLine> theatre = analytics.topItems(
                YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.THEATRE_PROCEDURE, 5);

        assertThat(surgery).extracting(TopServiceItemLine::name).containsExactly("Caesarean section");
        assertThat(theatre).extracting(TopServiceItemLine::name).containsExactly("Wound suturing");
    }

    @Test
    void topItemsRespectsTheRequestedLimitAndTheServerCap() {
        Fixture f = fixture();
        for (int i = 0; i < 4; i++) {
            postCharge(f, namedItem("PHARMACY", "PHARMACY", 1_000 * (i + 1), "Drug " + i), 1);
        }

        assertThat(analytics.topItems(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.PHARMACY, 2))
                .hasSize(2);
        // Cap, not the requested 10_000.
        assertThat(analytics.topItems(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.PHARMACY, 10_000))
                .hasSize(4);
    }

    @Test
    void topItemsIsEmptyRatherThanFabricatedWhenNothingWasBilled() {
        assertThat(analytics.topItems(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID, RevenueCategory.PHARMACY, 5))
                .isEmpty();
    }

    // ------------------------------------------------------------- trend

    @Test
    void dailyTrendReturnsOneBucketPerDayIncludingDaysWithNoActivity() {
        Fixture f = fixture();
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 40_000);

        List<RevenueTrendPoint> points = analytics.trend(
                TODAY.minusDays(2), TODAY, BOOTSTRAP_FACILITY_ID, TrendGranularity.DAY);

        assertThat(points).hasSize(3);
        assertThat(points).extracting(RevenueTrendPoint::bucketStart)
                .containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
        assertThat(points.get(0).billedMinor()).isZero();
        assertThat(points.get(2).billedMinor()).isEqualTo(40_000);
        assertThat(points.get(2).collectedMinor()).isEqualTo(40_000);
    }

    @Test
    void trendTotalsReconcileWithTheSummaryOverTheSamePeriod() {
        Fixture f = fixture();
        grantBypass(f);
        UUID a = postCharge(f, consultItem(40_000), 1);
        UUID b = postCharge(f, item("LAB", null, 20_000), 1);
        pay(f, a, 25_000);
        pay(f, b, 20_000);

        List<RevenueTrendPoint> points = analytics.trend(
                TODAY.minusDays(3), TOMORROW, BOOTSTRAP_FACILITY_ID, TrendGranularity.DAY);
        RevenueSummary summary = analytics.summary(TODAY.minusDays(3), TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(points.stream().mapToLong(RevenueTrendPoint::billedMinor).sum())
                .isEqualTo(summary.grossBilledMinor());
        assertThat(points.stream().mapToLong(RevenueTrendPoint::collectedMinor).sum())
                .isEqualTo(summary.collectedCashMinor());
    }

    @Test
    void monthlyTrendBucketsOnTheFirstOfTheMonth() {
        List<RevenueTrendPoint> points = analytics.trend(
                TODAY.minusMonths(2), TODAY, BOOTSTRAP_FACILITY_ID, TrendGranularity.MONTH);

        assertThat(points).extracting(RevenueTrendPoint::bucketStart)
                .allSatisfy(bucket -> assertThat(bucket.getDayOfMonth()).isEqualTo(1));
        assertThat(points).hasSize(3);
    }

    @Test
    void weeklyTrendBucketsStartOnMonday() {
        List<RevenueTrendPoint> points = analytics.trend(
                TODAY.minusWeeks(3), TODAY, BOOTSTRAP_FACILITY_ID, TrendGranularity.WEEK);

        assertThat(points).extracting(RevenueTrendPoint::bucketStart)
                .allSatisfy(bucket -> assertThat(bucket.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY));
    }

    @Test
    void anUnreadablyLongDailyTrendIsRefusedRatherThanReturned() {
        assertThatThrownBy(() -> analytics.trend(
                TODAY.minusYears(3), TODAY, BOOTSTRAP_FACILITY_ID, TrendGranularity.DAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("400-point limit");
    }

    @Test
    void reversedRangeIsRefused() {
        assertThatThrownBy(() -> analytics.trend(
                TOMORROW, YESTERDAY, BOOTSTRAP_FACILITY_ID, TrendGranularity.DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------- date basis guarantees

    // The contract every label on these screens depends on: billed is dated
    // by the charge, collected by the payment. A payment that settles an
    // older charge belongs to the period it was RECEIVED in, and must not
    // drag the charge's billed amount forward with it.
    @Test
    void aPaymentThisPeriodForAChargeBilledEarlierCountsAsCollectedOnlyNowAndBilledOnlyThen() {
        Fixture f = fixture();
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 40_000);
        // The charge was raised 10 days ago; the money arrived today.
        jdbcTemplate.update("UPDATE invoice_line SET created_at = now() - interval '10 days'",
                new MapSqlParameterSource());
        jdbcTemplate.update("UPDATE invoice SET created_at = now() - interval '10 days'",
                new MapSqlParameterSource());

        RevenueSummary thisPeriod = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);
        RevenueSummary earlierPeriod = analytics.summary(
                TODAY.minusDays(12), TODAY.minusDays(8), BOOTSTRAP_FACILITY_ID);

        assertThat(thisPeriod.collectedCashMinor()).isEqualTo(40_000);
        assertThat(thisPeriod.grossBilledMinor()).isZero();
        assertThat(earlierPeriod.grossBilledMinor()).isEqualTo(40_000);
        assertThat(earlierPeriod.collectedCashMinor()).isZero();
    }

    // The collection rate is a cohort ratio: of what was billed in this
    // period, how much has been settled to date. A payment for an older
    // charge must not inflate it — that is exactly the fiction a
    // collected-over-billed ratio produces.
    @Test
    void collectionRateCohortIgnoresPaymentsForChargesBilledOutsideThePeriod() {
        Fixture f = fixture();
        UUID oldLine = postCharge(f, consultItem(40_000), 1);
        pay(f, oldLine, 40_000);
        jdbcTemplate.update("UPDATE invoice_line SET created_at = now() - interval '10 days'",
                new MapSqlParameterSource());
        // A fresh, wholly unpaid charge in the period under test.
        postCharge(f, item("LAB", null, 20_000), 1);

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.cohortPatientPortionBilledMinor()).isEqualTo(20_000);
        assertThat(summary.cohortSettledMinor()).isZero();
        // Collected is non-zero on the payment-date basis, which is why the
        // rate deliberately does not use it as its numerator.
        assertThat(summary.collectedCashMinor()).isEqualTo(40_000);
    }

    // Over-allocating a line is blocked in the domain, so a cohort rate
    // above 100% cannot arise through the payment service at all.
    @Test
    void theDomainRefusesToAllocateMoreThanALineOutstanding() {
        Fixture f = fixture();
        grantBypass(f);
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 30_000);

        assertThatThrownBy(() -> pay(f, line, 30_000))
                .isInstanceOf(PaymentAllocationRejectedException.class);
    }

    // Defence in depth for the same invariant: the report's own LEAST clamp
    // must hold even against a row the service layer could not have
    // produced, so a corrupted allocation cannot push the rate past 100%.
    @Test
    void cohortSettledIsClampedToBilledEvenIfAnAllocationExceedsTheLine() {
        Fixture f = fixture();
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 40_000);
        jdbcTemplate.update("""
                        UPDATE payment_allocation SET amount_minor = 90000
                        WHERE invoice_line_id = :lineId
                        """, new MapSqlParameterSource("lineId", line));

        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(summary.cohortPatientPortionBilledMinor()).isEqualTo(40_000);
        assertThat(summary.cohortSettledMinor()).isEqualTo(40_000);
    }

    // ------------------------------------------------------- adjustments

    @Test
    void adjustmentsAreZeroWhenNoneHaveBeenApplied() {
        FinancialAdjustments adjustments = analytics.adjustments(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(adjustments.creditNotesAppliedMinor()).isZero();
        assertThat(adjustments.writeOffsAppliedMinor()).isZero();
        assertThat(adjustments.totalAppliedMinor()).isZero();
        assertThat(adjustments.pendingCreditNoteCount()).isZero();
    }

    @Test
    void appliedCreditNoteIsReportedAndDoesNotTouchCategoryBilled() {
        Fixture f = fixture();
        UUID line = postCharge(f, item("LAB", null, 20_000), 1);
        UUID invoiceId = invoiceIdOfLine(line);
        insertCreditNote(invoiceId, 5_000, "APPLIED", "now()");

        FinancialAdjustments adjustments = analytics.adjustments(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);
        Map<RevenueCategory, Long> billed =
                billedByCategory(analytics.byCategory(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID));

        assertThat(adjustments.creditNotesAppliedMinor()).isEqualTo(5_000);
        assertThat(adjustments.creditNotesAppliedCount()).isEqualTo(1);
        // Gross of adjustments — the credit note belongs to the invoice, not
        // to the laboratory category, and is never netted off one.
        assertThat(billed.get(RevenueCategory.LABORATORY)).isEqualTo(20_000);
    }

    @Test
    void pendingAndRejectedAdjustmentsAreNotCountedAsApplied() {
        Fixture f = fixture();
        UUID invoiceId = invoiceIdOfLine(postCharge(f, item("LAB", null, 20_000), 1));
        insertCreditNote(invoiceId, 5_000, "PENDING", null);
        insertCreditNote(invoiceId, 7_000, "REJECTED", "now()");

        FinancialAdjustments adjustments = analytics.adjustments(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(adjustments.creditNotesAppliedMinor()).isZero();
        assertThat(adjustments.creditNotesAppliedCount()).isZero();
        assertThat(adjustments.pendingCreditNoteCount()).isEqualTo(1);
    }

    @Test
    void appliedWriteOffIsReportedSeparatelyFromCreditNotes() {
        Fixture f = fixture();
        UUID invoiceId = invoiceIdOfLine(postCharge(f, item("LAB", null, 20_000), 1));
        insertWriteOff(invoiceId, 9_000, "APPLIED", "now()");

        FinancialAdjustments adjustments = analytics.adjustments(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(adjustments.writeOffsAppliedMinor()).isEqualTo(9_000);
        assertThat(adjustments.creditNotesAppliedMinor()).isZero();
        assertThat(adjustments.totalAppliedMinor()).isEqualTo(9_000);
    }

    @Test
    void adjustmentsAppliedOutsideThePeriodAreExcluded() {
        Fixture f = fixture();
        UUID invoiceId = invoiceIdOfLine(postCharge(f, item("LAB", null, 20_000), 1));
        insertCreditNote(invoiceId, 5_000, "APPLIED", "now() - interval '20 days'");

        FinancialAdjustments adjustments = analytics.adjustments(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(adjustments.creditNotesAppliedMinor()).isZero();
    }

    @Test
    void refundsSurfaceInAdjustmentsAndAreAlreadyNettedOutOfCollected() {
        Fixture f = fixture();
        UUID line = postCharge(f, consultItem(40_000), 1);
        pay(f, line, 40_000);
        refundAllocation(line, 10_000);

        FinancialAdjustments adjustments = analytics.adjustments(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);
        RevenueSummary summary = analytics.summary(YESTERDAY, TOMORROW, BOOTSTRAP_FACILITY_ID);

        assertThat(adjustments.refundsAppliedMinor()).isEqualTo(10_000);
        assertThat(summary.collectedCashMinor()).isEqualTo(30_000);
        // Refunds are not part of the receivable-reducing total: they move
        // cash back out, they do not forgive a charge.
        assertThat(adjustments.totalAppliedMinor()).isZero();
    }

    // ---------------------------------------------------------- fixtures

    private record Fixture(UUID admin, UUID patientId, UUID encounterId, UUID facilityId) {
    }

    private Fixture fixture() {
        return fixtureAt(BOOTSTRAP_FACILITY_ID);
    }

    private Fixture fixtureAt(UUID facilityId) {
        UUID admin = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
        UUID patientId = jdbcTemplate.queryForObject("""
                        INSERT INTO patient (hospital_no, given_name, family_name, date_of_birth, gender, phone)
                        VALUES (:hospitalNo, 'Test', 'Patient', DATE '1990-01-01', 'MALE', '+255700000000')
                        RETURNING id
                        """, new MapSqlParameterSource("hospitalNo", "TEST-" + UUID.randomUUID()), UUID.class);
        UUID departmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO department (facility_id, category_id, code, name) VALUES (:facilityId, (SELECT id FROM department_category WHERE code = 'GENERAL_CLINICAL'), :code, :code) RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("facilityId", facilityId)
                        .addValue("code", "TESTDEPT-" + UUID.randomUUID().toString().substring(0, 8)), UUID.class);
        UUID encounterId = jdbcTemplate.queryForObject("""
                        INSERT INTO encounter (patient_id, facility_id, department_id, type, state, payer_type, created_by)
                        VALUES (:patientId, :facilityId, :departmentId, 'OUTPATIENT', 'AWAITING_PAYMENT', 'CASH', :admin)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("patientId", patientId)
                        .addValue("facilityId", facilityId)
                        .addValue("departmentId", departmentId)
                        .addValue("admin", admin), UUID.class);
        if (facilityId.equals(BOOTSTRAP_FACILITY_ID)) {
            openCashierShiftService.open(facilityId, admin);
        }
        return new Fixture(admin, patientId, encounterId, facilityId);
    }

    private UUID postCharge(Fixture f, String serviceItemCode, int quantity) {
        ChargePostingResult result = chargePostingService.postCharge(new ChargePostingCommand(
                f.encounterId, f.patientId, f.facilityId, PayerCategory.CASH, null,
                serviceItemCode, quantity, "TestFixture", UUID.randomUUID(), f.admin));
        return result.invoiceLineId();
    }

    /** What makes a partial allocation legal — 02-billing-architecture.md §5.1. */
    private void grantBypass(Fixture f) {
        paymentBypassRepository.insert(PaymentBypass.grant(
                f.encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, "Fixture: partial payment", f.admin));
    }

    private void pay(Fixture f, UUID invoiceLineId, long amountMinor) {
        postPaymentService.recordPayment(new PostPaymentCommand(f.patientId, PaymentMethod.CASH, null,
                "idem-" + UUID.randomUUID(),
                List.of(new PostPaymentCommand.LineAllocationRequest(invoiceLineId, amountMinor)), f.admin));
    }

    /** An approved refund's only ledger effect: refunded_minor on the allocation. */
    private void refundAllocation(UUID invoiceLineId, long amountMinor) {
        jdbcTemplate.update("""
                        UPDATE payment_allocation SET refunded_minor = refunded_minor + :amountMinor
                        WHERE invoice_line_id = :invoiceLineId
                        """,
                new MapSqlParameterSource()
                        .addValue("amountMinor", amountMinor)
                        .addValue("invoiceLineId", invoiceLineId));
    }

    private UUID invoiceIdOfLine(UUID invoiceLineId) {
        return jdbcTemplate.queryForObject("SELECT invoice_id FROM invoice_line WHERE id = :id",
                new MapSqlParameterSource("id", invoiceLineId), UUID.class);
    }

    /** Mirrors what ReviewCreditNoteService leaves behind; decidedAt is a SQL expression or null. */
    private void insertCreditNote(UUID invoiceId, long amountMinor, String status, String decidedAtSql) {
        jdbcTemplate.update("""
                        INSERT INTO credit_note (invoice_id, amount_minor, reason, issued_by,
                                                 requires_second_signature, status, approved_by, approved_at)
                        VALUES (:invoiceId, :amountMinor, 'Fixture', :admin, FALSE, :status,
                                %s, %s)
                        """.formatted(
                                decidedAtSql == null ? "NULL" : ":admin",
                                decidedAtSql == null ? "NULL" : decidedAtSql),
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("amountMinor", amountMinor)
                        .addValue("status", status)
                        .addValue("admin", bootstrapAdminId()));
    }

    private void insertWriteOff(UUID invoiceId, long amountMinor, String status, String decidedAtSql) {
        jdbcTemplate.update("""
                        INSERT INTO invoice_write_off (invoice_id, amount_minor, reason, requested_by,
                                                       requires_second_signature, status, decided_by, decided_at)
                        VALUES (:invoiceId, :amountMinor, 'Fixture', :admin, FALSE, :status,
                                %s, %s)
                        """.formatted(
                                decidedAtSql == null ? "NULL" : ":admin",
                                decidedAtSql == null ? "NULL" : decidedAtSql),
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("amountMinor", amountMinor)
                        .addValue("status", status)
                        .addValue("admin", bootstrapAdminId()));
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username = 'admin'",
                new MapSqlParameterSource(), UUID.class);
    }

    private String itemWithBillingCategory(String category, String type, String billingCategory, long amountMinor) {
        String code = namedItem(category, type, amountMinor, "Fixture " + billingCategory);
        jdbcTemplate.update("UPDATE service_item SET billing_category = :bc WHERE code = :code",
                new MapSqlParameterSource().addValue("bc", billingCategory).addValue("code", code));
        return code;
    }

    private String consultItem(long amountMinor) {
        return item("CONSULTATION", null, amountMinor);
    }

    private String item(String category, String type, long amountMinor) {
        return namedItem(category, type, amountMinor, "Fixture " + category);
    }

    private String namedItem(String category, String type, long amountMinor, String name) {
        String code = "TEST-" + UUID.randomUUID().toString().substring(0, 12);
        jdbcTemplate.update("""
                        INSERT INTO service_item (code, name, category, type)
                        VALUES (:code, :name, :category, :type)
                        """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("name", name)
                        .addValue("category", category)
                        .addValue("type", type));
        jdbcTemplate.update("""
                        INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
                        VALUES ((SELECT id FROM price_list WHERE payer_category = 'CASH'),
                                (SELECT id FROM service_item WHERE code = :code),
                                :amountMinor,
                                (SELECT id FROM app_user WHERE username = 'admin'))
                        """,
                new MapSqlParameterSource().addValue("code", code).addValue("amountMinor", amountMinor));
        return code;
    }

    private Map<RevenueCategory, Long> billedByCategory(List<CategoryRevenueLine> lines) {
        return lines.stream().collect(java.util.stream.Collectors.toMap(
                CategoryRevenueLine::category, CategoryRevenueLine::billedMinor));
    }
}
