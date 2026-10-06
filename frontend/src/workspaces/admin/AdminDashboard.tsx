import { PageHeader } from '../../shared/components/PageHeader';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { MetricCard as StatCard } from '../../shared/components/MetricCard';
import { QueryState } from '../../shared/components/WorkspacePage';
import { ArrowUpRight, Users, Tags, Building2, Receipt } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { fetchPendingCancellationRequests } from '../../shared/api/billing';
import {
  fetchExpensesByStatus,
  fetchReceivablesOverview,
  fetchRevenueReport,
} from '../../shared/api/finance';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';

// 18-admin.md §1: a landing page an admin actually uses — pending items
// that need their attention, plus the two financial totals that anchor
// the day (cash in, receivables outstanding). Every number is a link to
// the screen where the admin can act on it, per the frontend CLAUDE.md
// "Every count badge is clickable and leads to the filtered list."
//
// Nothing here computes a "profit" or a single "revenue" figure —
// 02-billing-architecture.md §13 and the RevenueReport DTO's own comment
// forbid summing collected cash and insurance billed into one number.
export function AdminDashboard() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const facilityId = useAdminFacilityId();

  const canApproveCancellations = usePermission('INVOICE_CANCEL_APPROVE');
  const canApproveExpenses = usePermission('EXPENSE_APPROVE');
  const canReadReports = usePermission('FINANCIAL_REPORT_READ');

  const monthRange = getCurrentMonthRange();

  const { data: pendingCancellations, isError: cancellationError, refetch: retryCancellations } = useQuery({
    queryKey: ['cancellation-requests', 'pending'],
    queryFn: () => fetchPendingCancellationRequests(accessToken as string),
    enabled: accessToken != null && canApproveCancellations,
  });

  const { data: pendingExpenses, isError: expenseError, refetch: retryExpenses } = useQuery({
    queryKey: ['expenses', 'PENDING_APPROVAL'],
    queryFn: () => fetchExpensesByStatus('PENDING_APPROVAL', accessToken as string),
    enabled: accessToken != null && canApproveExpenses,
  });

  const { data: revenue, isError: revenueError, refetch: retryRevenue } = useQuery({
    queryKey: ['reporting', 'revenue', monthRange.from, monthRange.to, facilityId],
    queryFn: () =>
      fetchRevenueReport(monthRange.from, monthRange.to, facilityId as string, accessToken as string),
    enabled: accessToken != null && canReadReports && !!facilityId,
  });

  const { data: receivables, isError: receivablesError, refetch: retryReceivables } = useQuery({
    queryKey: ['reporting', 'receivables-overview', facilityId],
    queryFn: () => fetchReceivablesOverview(facilityId as string, accessToken as string),
    enabled: accessToken != null && canReadReports && !!facilityId,
  });

  return (
    <div className="admin-overview">
      <PageHeader title="Hospital overview" description={`Welcome${me?.username ? `, ${me.username}` : ''}. Review hospital finances and work awaiting your attention.`}>
        <span className="dashboard-period">{formatMonthLabel(monthRange.from)}</span>
      </PageHeader>
      {(cancellationError || expenseError || revenueError || receivablesError) && <QueryState title="Some dashboard information could not be loaded" description="Available figures remain visible below. Retry to refresh the missing information." onRetry={() => {
        if (cancellationError) void retryCancellations(); if (expenseError) void retryExpenses();
        if (revenueError) void retryRevenue(); if (receivablesError) void retryReceivables();
      }} />}
      <div className="dashboard-actions" aria-label="Administrative actions">
        <DashboardAction to="/admin/users" permission="USER_MANAGE" title="Staff & access" description="Manage accounts and role assignments" icon={Users} />
        <DashboardAction to="/admin/pricing" permission="PHARMACY_ITEM_APPROVE" title="Catalogue & pricing" description="Review items and price proposals" icon={Tags} />
        <DashboardAction to="/admin/theatres" permission="THEATRE_MANAGE" title="Operating theatres" description="Manage theatre availability" icon={Building2} />
        <DashboardAction to="/admin/revenue" permission="INVOICE_CANCEL_APPROVE" title="Revenue & invoices" description="Review collections and cancellations" icon={Receipt} />
      </div>

      {me?.mustChangePassword && (
        <p
          role="alert"
          style={{
            marginTop: 0,
            marginBottom: 'var(--space-4)',
            padding: 'var(--space-3)',
            background: 'var(--warning-bg)',
            color: 'var(--warning)',
            borderRadius: 'var(--radius)',
            maxWidth: 480,
          }}
        >
          Your password was set at install time — change it before continuing.
        </p>
      )}

      {(canApproveCancellations || canApproveExpenses) && <section className="dashboard-section dashboard-section--attention">
        <h2>Needs your attention</h2>
        <div className="metric-grid">
          {canApproveCancellations && (
            <StatCard
              to="/admin/revenue"
              label="Cancellation requests"
              value={pendingCancellations?.length ?? '—'}
              hint="Pending review"
              tone={pendingCancellations && pendingCancellations.length > 0 ? 'warning' : 'neutral'}
            />
          )}
          {canApproveExpenses && (
            <StatCard
              to="/admin/expense-analytics"
              label="Expenses awaiting approval"
              value={pendingExpenses?.length ?? '—'}
              hint="Across all categories"
              tone={pendingExpenses && pendingExpenses.length > 0 ? 'warning' : 'neutral'}
            />
          )}
        </div>
      </section>}

      {canReadReports && (
        <section className="dashboard-section">
          <h2>This month</h2>
          {!facilityId ? (
            <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
              Grant yourself a facility-scoped role to see financial totals.
            </p>
          ) : (
            <div className="metric-grid">
              <StatCard
                label="Cash collected"
                value={revenue ? formatMoney(revenue.collectedCashMinor, revenue.currencyCode) : '—'}
                hint="Settled payments"
              />
              <StatCard
                label="Insurance billed"
                value={revenue ? formatMoney(revenue.insuranceBilledMinor, revenue.currencyCode) : '—'}
                hint="Not cash until remittance clears"
              />
              <StatCard
                label="Emergency bypass debt"
                value={revenue ? formatMoney(revenue.emergencyBypassDebtMinor, revenue.currencyCode) : '—'}
                hint="Point in time"
              />
            </div>
          )}
        </section>
      )}

      {canReadReports && facilityId && (
        <section className="dashboard-section">
          <h2>Receivables</h2>
          <div className="metric-grid">
            <StatCard
              label="Post-discharge outstanding"
              value={
                receivables
                  ? formatMoney(receivables.postDischargeOutstandingMinor, receivables.currencyCode)
                  : '—'
              }
              hint={
                receivables
                  ? `${receivables.postDischargeInvoiceCount ?? 0} invoice(s)`
                  : undefined
              }
              tone={
                receivables && (receivables.postDischargeOutstandingMinor ?? 0) > 0 ? 'warning' : 'neutral'
              }
            />
            <StatCard
              label="Active encounters outstanding"
              value={
                receivables
                  ? formatMoney(receivables.activeEncounterOutstandingMinor, receivables.currencyCode)
                  : '—'
              }
              hint={
                receivables
                  ? `${receivables.activeEncounterInvoiceCount ?? 0} invoice(s) — governed by the billing gate`
                  : undefined
              }
            />
          </div>
        </section>
      )}
    </div>
  );
}

function DashboardAction({ to, permission, title, description, icon: Icon }: { to: string; permission: string; title: string; description: string; icon: LucideIcon }) {
  const allowed = usePermission(permission);
  if (!allowed) return null;
  return <Link to={to} className="dashboard-action"><span className="dashboard-action-icon"><Icon size={20} aria-hidden="true" /></span>
    <span><strong>{title}</strong><small>{description}</small></span><ArrowUpRight size={16} aria-hidden="true" /></Link>;
}

function getCurrentMonthRange(): { from: string; to: string } {
  const now = new Date();
  const from = new Date(now.getFullYear(), now.getMonth(), 1);
  const to = now;
  return { from: toIsoDate(from), to: toIsoDate(to) };
}

function toIsoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function formatMonthLabel(fromIso: string): string {
  const d = new Date(fromIso);
  return new Intl.DateTimeFormat('en-TZ', { month: 'long', year: 'numeric' }).format(d);
}
