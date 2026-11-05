import { PageHeader } from '../../shared/components/PageHeader';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { QueryState } from '../../shared/components/WorkspacePage';
import { ChartCard } from '../../shared/components/charts/ChartCard';
import { CategoryBarChart } from '../../shared/components/charts/CategoryBarChart';
import { KpiCard } from '../../shared/components/charts/KpiCard';
import { RankedBarChart, type RankedBarRow } from '../../shared/components/charts/RankedBarChart';
import { TrendAreaChart } from '../../shared/components/charts/TrendAreaChart';
import { resolveRange } from '../../shared/components/charts/dateRange';
import {
  ArrowUpRight,
  Building2,
  ClipboardList,
  Clock3,
  Receipt,
  Tags,
  Users,
  Wallet,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { fetchPendingCancellationRequests } from '../../shared/api/billing';
import { fetchExpensesByStatus, fetchReceivablesOverview } from '../../shared/api/finance';
import {
  fetchRevenueByCategory,
  fetchRevenueTrend,
  fetchTopRevenueItems,
} from '../../shared/api/revenueAnalytics';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';

// 18-admin.md §1: a landing page an admin actually uses — the work awaiting
// them, then the shape of the month's money, then what is owed. Every number
// is a link to the screen where it can be acted on where such a screen
// exists (frontend CLAUDE.md: "A number a user cannot click is a dead end"),
// and no tile links nowhere just to look clickable.
//
// Nothing here sums collected cash and insurance billed into one "revenue"
// figure — 02-billing-architecture.md §13 forbids it, and the two charts
// below keep them as separate, labelled series instead.
//
// Emergency bypass debt is deliberately absent from this presentation. The
// underlying records, the bypass mechanism and RevenueReportService
// .emergencyBypassDebtMinor are all untouched; it is simply not a headline
// an admin reads at a glance, and it sat beside period flows while being a
// point-in-time balance.
const TOP_N = 5;

export function AdminDashboard() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const facilityId = useAdminFacilityId();

  const canApproveCancellations = usePermission('INVOICE_CANCEL_APPROVE');
  const canApproveExpenses = usePermission('EXPENSE_APPROVE');
  const canReadReports = usePermission('FINANCIAL_REPORT_READ');

  // The overview is always month-to-date; /admin/revenue is where a period
  // gets chosen. Two screens that both ask "which period?" make the admin
  // answer the same question twice.
  const range = resolveRange('MTD', { from: '', to: '' });
  const reportsEnabled = accessToken != null && canReadReports && !!facilityId;

  const cancellations = useQuery({
    queryKey: ['cancellation-requests', 'pending'],
    queryFn: () => fetchPendingCancellationRequests(accessToken as string),
    enabled: accessToken != null && canApproveCancellations,
  });

  const expenses = useQuery({
    queryKey: ['expenses', 'PENDING_APPROVAL'],
    queryFn: () => fetchExpensesByStatus('PENDING_APPROVAL', accessToken as string),
    enabled: accessToken != null && canApproveExpenses,
  });

  const byCategory = useQuery({
    queryKey: ['reporting', 'revenue', 'by-category', range.from, range.to, facilityId],
    queryFn: () => fetchRevenueByCategory(range, facilityId as string, accessToken as string),
    enabled: reportsEnabled,
  });

  const trend = useQuery({
    queryKey: ['reporting', 'revenue', 'trend', range.from, range.to, facilityId],
    queryFn: () => fetchRevenueTrend(range, facilityId as string, accessToken as string),
    enabled: reportsEnabled,
  });

  const topMedicines = useQuery({
    queryKey: ['reporting', 'revenue', 'top', 'PHARMACY', range.from, range.to, facilityId],
    queryFn: () => fetchTopRevenueItems(range, facilityId as string, 'PHARMACY', TOP_N, accessToken as string),
    enabled: reportsEnabled,
  });

  const topLabTests = useQuery({
    queryKey: ['reporting', 'revenue', 'top', 'LABORATORY', range.from, range.to, facilityId],
    queryFn: () => fetchTopRevenueItems(range, facilityId as string, 'LABORATORY', TOP_N, accessToken as string),
    enabled: reportsEnabled,
  });

  const receivables = useQuery({
    queryKey: ['reporting', 'receivables-overview', facilityId],
    queryFn: () => fetchReceivablesOverview(facilityId as string, accessToken as string),
    enabled: reportsEnabled,
  });

  const attentionVisible = canApproveCancellations || canApproveExpenses;
  const pendingCancellationCount = cancellations.data?.length ?? 0;
  const pendingExpenseCount = expenses.data?.length ?? 0;

  return (
    <div className="analytics-page">
      <PageHeader
        title="Hospital overview"
        description={`Welcome${me?.username ? `, ${me.username}` : ''}. Work awaiting you, and how the month is tracking.`}
      >
        <span className="dashboard-period">{formatMonthLabel(range.from)}</span>
      </PageHeader>

      {(cancellations.isError || expenses.isError) && (
        <QueryState
          title="Some pending work could not be loaded"
          description="Available figures remain visible below. Retry to refresh the missing information."
          onRetry={() => {
            if (cancellations.isError) void cancellations.refetch();
            if (expenses.isError) void expenses.refetch();
          }}
        />
      )}

      {me?.mustChangePassword && (
        <p role="alert" className="form-error" style={{ margin: 0 }}>
          Your password was set at install time — change it before continuing.
        </p>
      )}

      {/* Row 1 — quick access */}
      <div className="dashboard-actions" aria-label="Administrative actions">
        <DashboardAction to="/admin/users" permission="USER_MANAGE" title="Staff & access" description="Accounts and role assignments" icon={Users} />
        <DashboardAction to="/admin/pricing" permission="PHARMACY_ITEM_APPROVE" title="Catalogue & pricing" description="Items and price proposals" icon={Tags} />
        <DashboardAction to="/admin/theatres" permission="THEATRE_MANAGE" title="Operating theatres" description="Theatre availability" icon={Building2} />
        <DashboardAction to="/admin/revenue" permission="INVOICE_CANCEL_APPROVE" title="Revenue & invoices" description="Analytics and cancellations" icon={Receipt} />
        <DashboardAction to="/admin/tat" permission="TAT_ANALYTICS_READ" title="Turnaround Time" description="Waiting and service monitoring" icon={Clock3} />
      </div>

      {/* Row 2 — pending actions */}
      {attentionVisible && (
        <section className="analytics-page" style={{ gap: 'var(--space-3)' }}>
          <div className="analytics-section-head">
            <h2>Needs your attention</h2>
            <p>Items that stay blocked until an admin decides.</p>
          </div>
          <div className="kpi-row kpi-row--two">
            {canApproveCancellations && (
              <KpiCard
                label="Invoice cancellation requests"
                value={cancellations.isPending ? '…' : pendingCancellationCount}
                basis={pendingCancellationCount > 0 ? 'Awaiting your review' : 'Nothing pending'}
                icon={ClipboardList}
                tone={pendingCancellationCount > 0 ? 'warning' : 'neutral'}
                to="/admin/revenue"
              />
            )}
            {canApproveExpenses && (
              <KpiCard
                label="Expenses awaiting approval"
                value={expenses.isPending ? '…' : pendingExpenseCount}
                basis={pendingExpenseCount > 0 ? 'Across all categories' : 'Nothing pending'}
                icon={Wallet}
                tone={pendingExpenseCount > 0 ? 'warning' : 'neutral'}
                to="/admin/expense-analytics"
              />
            )}
          </div>
        </section>
      )}

      {!canReadReports ? null : !facilityId ? (
        <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
          Grant yourself a facility-scoped role to see financial figures.
        </p>
      ) : (
        <>
          <div className="analytics-section-head">
            <h2>Revenue overview</h2>
            <Link to="/admin/revenue" className="link-button">
              Full revenue analytics <ArrowUpRight size={14} aria-hidden="true" />
            </Link>
          </div>

          {/* Row 3 — category mix and collection trend */}
          <div className="analytics-row">
            <ChartCard
              title="Revenue by service category"
              basis="Billed (gross of invoice-level credit notes and write-offs): charges raised this month. Collected: payments received this month, net of refunds."
              isLoading={byCategory.isPending}
              isError={byCategory.isError}
              onRetry={() => void byCategory.refetch()}
              isEmpty={(byCategory.data ?? []).every((line) => !line.billedMinor && !line.collectedMinor)}
              emptyMessage="No charges or payments this month."
            >
              <CategoryBarChart
                primaryLabel="Collected"
                secondaryLabel="Billed"
                currencyCode={byCategory.data?.[0]?.currencyCode}
                bars={(byCategory.data ?? []).map((line) => ({
                  id: line.category ?? line.categoryLabel ?? '',
                  label: line.categoryLabel ?? '',
                  primary: line.collectedMinor ?? 0,
                  secondary: line.billedMinor ?? 0,
                }))}
              />
            </ChartCard>

            <ChartCard
              title="Collection trend"
              basis="Daily. Billed by charge date, collected by payment date — a payment may settle a charge raised earlier."
              isLoading={trend.isPending}
              isError={trend.isError}
              onRetry={() => void trend.refetch()}
              isEmpty={(trend.data ?? []).every((p) => !p.billedMinor && !p.collectedMinor)}
              emptyMessage="No billing or collection activity this month."
            >
              <TrendAreaChart
                granularityLabel="Daily"
                currencyCode={trend.data?.[0]?.currencyCode}
                points={(trend.data ?? []).map((point) => ({
                  bucket: point.bucketStart ?? '',
                  billed: point.billedMinor ?? 0,
                  collected: point.collectedMinor ?? 0,
                }))}
              />
            </ChartCard>
          </div>

          {/* Row 4 — top medicines and lab tests */}
          <div className="analytics-row">
            <ChartCard
              title="Top medicines by revenue"
              basis="Quantity BILLED, from invoice lines — not quantity dispensed. Dispensing is gated on payment, so the two legitimately differ."
              isLoading={topMedicines.isPending}
              isError={topMedicines.isError}
              onRetry={() => void topMedicines.refetch()}
              isEmpty={(topMedicines.data ?? []).length === 0}
              emptyMessage="No medicines billed this month."
            >
              <RankedBarChart
                valueLabel="Collected"
                secondaryLabel="Billed"
                currencyCode={topMedicines.data?.[0]?.currencyCode}
                rows={(topMedicines.data ?? []).map(toMedicineRow)}
              />
            </ChartCard>

            <ChartCard
              title="Top laboratory tests by revenue"
              basis="Paid tests are lines billed this month whose patient portion is fully settled."
              isLoading={topLabTests.isPending}
              isError={topLabTests.isError}
              onRetry={() => void topLabTests.refetch()}
              isEmpty={(topLabTests.data ?? []).length === 0}
              emptyMessage="No laboratory tests billed this month."
            >
              <RankedBarChart
                valueLabel="Collected"
                secondaryLabel="Billed"
                currencyCode={topLabTests.data?.[0]?.currencyCode}
                rows={(topLabTests.data ?? []).map(toLabRow)}
              />
            </ChartCard>
          </div>

          {/* Row 5 — receivables */}
          <section className="analytics-page" style={{ gap: 'var(--space-3)' }}>
            <div className="analytics-section-head">
              <h2>Receivables</h2>
              <p>Owed now — a point-in-time balance, not a figure for the month.</p>
            </div>
            <div className="kpi-row kpi-row--two">
              <KpiCard
                label="Post-discharge outstanding"
                value={receivables.data ? formatMoney(receivables.data.postDischargeOutstandingMinor, receivables.data.currencyCode) : '—'}
                basis={
                  receivables.data
                    ? `${receivables.data.postDischargeInvoiceCount ?? 0} invoice(s) to chase`
                    : 'Loading…'
                }
                tone={(receivables.data?.postDischargeOutstandingMinor ?? 0) > 0 ? 'warning' : 'neutral'}
              />
              <KpiCard
                label="Active encounter outstanding"
                value={receivables.data ? formatMoney(receivables.data.activeEncounterOutstandingMinor, receivables.data.currencyCode) : '—'}
                basis={
                  receivables.data
                    ? `${receivables.data.activeEncounterInvoiceCount ?? 0} invoice(s) — governed by the billing gate, not yet chased`
                    : 'Loading…'
                }
              />
            </div>
          </section>
        </>
      )}
    </div>
  );
}

// Strength comes from the stock catalogue where service_item.code matches
// item.code; absent means no matching stock item, so the billed name stands
// on its own (pharmacy names already carry the strength: "Fluconazole 200mg").
function toMedicineRow(line: {
  serviceItemId?: string;
  name?: string;
  strength?: string;
  form?: string;
  quantityBilled?: number;
  collectedMinor?: number;
  billedMinor?: number;
}): RankedBarRow {
  const descriptors = [line.strength, line.form].filter(Boolean).join(' · ');
  return {
    id: line.serviceItemId ?? '',
    label: line.name ?? 'Unnamed item',
    sublabel: descriptors || undefined,
    value: line.collectedMinor ?? 0,
    secondaryValue: line.billedMinor ?? 0,
    meta: `Qty billed ${line.quantityBilled ?? 0} (not dispensed)`,
  };
}

function toLabRow(line: {
  serviceItemId?: string;
  name?: string;
  billedLineCount?: number;
  settledLineCount?: number;
  collectedMinor?: number;
  billedMinor?: number;
}): RankedBarRow {
  return {
    id: line.serviceItemId ?? '',
    label: line.name ?? 'Unnamed test',
    value: line.collectedMinor ?? 0,
    secondaryValue: line.billedMinor ?? 0,
    meta: `${line.settledLineCount ?? 0} paid of ${line.billedLineCount ?? 0} billed`,
  };
}

function DashboardAction({
  to,
  permission,
  title,
  description,
  icon: Icon,
}: {
  to: string;
  permission: string;
  title: string;
  description: string;
  icon: LucideIcon;
}) {
  const allowed = usePermission(permission);
  if (!allowed) return null;
  return (
    <Link to={to} className="dashboard-action">
      <span className="dashboard-action-icon">
        <Icon size={20} aria-hidden="true" />
      </span>
      <span>
        <strong>{title}</strong>
        <small>{description}</small>
      </span>
      <ArrowUpRight size={16} aria-hidden="true" />
    </Link>
  );
}

function formatMonthLabel(fromIso: string): string {
  return new Intl.DateTimeFormat('en-TZ', { month: 'long', year: 'numeric' }).format(new Date(`${fromIso}T00:00`));
}
