package com.adags.modules.reporting.api;

import com.adags.modules.reporting.application.ClaimsReportService;
import com.adags.modules.reporting.application.ClaimsSummaryLine;
import com.adags.modules.reporting.application.DepartmentContribution;
import com.adags.modules.reporting.application.DepartmentContributionService;
import com.adags.modules.reporting.application.ExpenseCategoryTotal;
import com.adags.modules.reporting.application.ExpenseMonthlyTotal;
import com.adags.modules.reporting.application.ExpenseReportService;
import com.adags.modules.reporting.application.CategoryRevenueLine;
import com.adags.modules.reporting.application.FinancialAdjustments;
import com.adags.modules.reporting.application.ReceivablesOverview;
import com.adags.modules.reporting.application.ReceivablesOverviewService;
import com.adags.modules.reporting.application.RevenueAnalyticsService;
import com.adags.modules.reporting.application.RevenueCategory;
import com.adags.modules.reporting.application.RevenueReport;
import com.adags.modules.reporting.application.RevenueReportService;
import com.adags.modules.reporting.application.RevenueSummary;
import com.adags.modules.reporting.application.RevenueTrendPoint;
import com.adags.modules.reporting.application.TopServiceItemLine;
import com.adags.modules.reporting.application.TrendGranularity;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 02-billing-architecture.md §13 / 18-admin.md: every endpoint here is a
// read over the existing ledger — no new financial table backs any of
// them. FINANCIAL_REPORT_READ (V5) is already ADMIN's and ACCOUNTANT's.
@RestController
@RequestMapping("/api/reporting")
public class ReportingController {

    private final RevenueReportService revenueReportService;
    private final RevenueAnalyticsService revenueAnalyticsService;
    private final ExpenseReportService expenseReportService;
    private final DepartmentContributionService departmentContributionService;
    private final ClaimsReportService claimsReportService;
    private final ReceivablesOverviewService receivablesOverviewService;
    private final AuthorizationService authorizationService;

    public ReportingController(
            RevenueReportService revenueReportService,
            RevenueAnalyticsService revenueAnalyticsService,
            ExpenseReportService expenseReportService,
            DepartmentContributionService departmentContributionService,
            ClaimsReportService claimsReportService,
            ReceivablesOverviewService receivablesOverviewService,
            AuthorizationService authorizationService
    ) {
        this.revenueReportService = revenueReportService;
        this.revenueAnalyticsService = revenueAnalyticsService;
        this.expenseReportService = expenseReportService;
        this.departmentContributionService = departmentContributionService;
        this.claimsReportService = claimsReportService;
        this.receivablesOverviewService = receivablesOverviewService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/revenue")
    public RevenueReport revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return revenueReportService.generate(from, to, facilityId);
    }

    // 18-admin.md: the KPI row on /admin/revenue. Every figure's basis —
    // period flow vs. point-in-time balance, billed vs. collected — is
    // documented on RevenueSummary itself, because a number whose basis
    // the reader has to guess is worse than no number.
    @GetMapping("/revenue/summary")
    public RevenueSummary revenueSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return revenueAnalyticsService.summary(from, to, facilityId);
    }

    // Revenue by service category. Returns every category in a fixed order
    // including the ones that earned nothing — a zero is a finding.
    @GetMapping("/revenue/by-category")
    public List<CategoryRevenueLine> revenueByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return revenueAnalyticsService.byCategory(from, to, facilityId);
    }

    // Billed and collected over time. granularity omitted picks DAY / WEEK /
    // MONTH from the span rather than returning 400 daily points for a year.
    @GetMapping("/revenue/trend")
    public List<RevenueTrendPoint> revenueTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @RequestParam(required = false) TrendGranularity granularity,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        TrendGranularity resolved = granularity != null ? granularity : TrendGranularity.forSpan(from, to);
        return revenueAnalyticsService.trend(from, to, facilityId, resolved);
    }

    // Credit notes and write-offs applied in the period. A separate endpoint
    // because they are invoice-level: neither can be attributed to a service
    // category, so neither may be netted off one. See FinancialAdjustments.
    @GetMapping("/revenue/adjustments")
    public FinancialAdjustments revenueAdjustments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return revenueAnalyticsService.adjustments(from, to, facilityId);
    }

    // Ranked catalogue items inside one revenue category — medicines, lab
    // tests, procedures. Bounded server-side (MAX_TOP_ITEMS); this is a
    // ranking, not a catalogue export.
    @GetMapping("/revenue/top-items")
    public List<TopServiceItemLine> revenueTopItems(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @RequestParam RevenueCategory category,
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return revenueAnalyticsService.topItems(from, to, facilityId, category, limit);
    }

    @GetMapping("/expenses/by-category")
    public List<ExpenseCategoryTotal> expensesByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return expenseReportService.byCategory(from, to);
    }

    @GetMapping("/expenses/monthly-trend")
    public List<ExpenseMonthlyTotal> expensesMonthlyTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return expenseReportService.monthlyTrend(from, to);
    }

    @GetMapping("/department-contribution")
    public List<DepartmentContribution> departmentContribution(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return departmentContributionService.generate(from, to, facilityId);
    }

    @GetMapping("/claims")
    public List<ClaimsSummaryLine> claims(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID facilityId,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor);
        return claimsReportService.summarize(from, to, facilityId);
    }

    // 18-admin.md item 5: confirms the existing aged-receivables view
    // (item 1) covers only post-discharge receivables, and surfaces the
    // active-encounter figure it deliberately excludes, side by side —
    // so nobody mistakes the two for the same kind of balance.
    @GetMapping("/receivables-overview")
    public ReceivablesOverview receivablesOverview(@RequestParam UUID facilityId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor);
        return receivablesOverviewService.generate(facilityId);
    }

    private void requirePermission(Actor actor) {
        if (!authorizationService.hasPermission(actor, "FINANCIAL_REPORT_READ")) {
            throw new AccessDeniedException("Missing permission: FINANCIAL_REPORT_READ");
        }
    }
}
