import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import type { CSSProperties, ReactNode } from 'react';
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

  const { data: pendingCancellations } = useQuery({
    queryKey: ['cancellation-requests', 'pending'],
    queryFn: () => fetchPendingCancellationRequests(accessToken as string),
    enabled: accessToken != null && canApproveCancellations,
  });

  const { data: pendingExpenses } = useQuery({
    queryKey: ['expenses', 'PENDING_APPROVAL'],
    queryFn: () => fetchExpensesByStatus('PENDING_APPROVAL', accessToken as string),
    enabled: accessToken != null && canApproveExpenses,
  });

  const { data: revenue } = useQuery({
    queryKey: ['reporting', 'revenue', monthRange.from, monthRange.to, facilityId],
    queryFn: () =>
      fetchRevenueReport(monthRange.from, monthRange.to, facilityId as string, accessToken as string),
    enabled: accessToken != null && canReadReports && !!facilityId,
  });

  const { data: receivables } = useQuery({
    queryKey: ['reporting', 'receivables-overview', facilityId],
    queryFn: () => fetchReceivablesOverview(facilityId as string, accessToken as string),
    enabled: accessToken != null && canReadReports && !!facilityId,
  });

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>
          Welcome{me?.username ? `, ${me.username}` : ''}
        </h1>
        <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
          {formatMonthLabel(monthRange.from)}
        </p>
      </header>

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

      <section style={{ marginBottom: 'var(--space-6)' }}>
        <h2 style={sectionHeadingStyle}>Needs your attention</h2>
        <div style={cardGridStyle}>
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
      </section>

      {canReadReports && (
        <section style={{ marginBottom: 'var(--space-6)' }}>
          <h2 style={sectionHeadingStyle}>This month</h2>
          {!facilityId ? (
            <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
              Grant yourself a facility-scoped role to see financial totals.
            </p>
          ) : (
            <div style={cardGridStyle}>
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
        <section>
          <h2 style={sectionHeadingStyle}>Receivables</h2>
          <div style={cardGridStyle}>
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

function StatCard({
  to,
  label,
  value,
  hint,
  tone = 'neutral',
}: {
  to?: string;
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: 'neutral' | 'warning';
}) {
  const body = (
    <div style={statCardStyle(tone, !!to)}>
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{label}</div>
      <div
        style={{
          marginTop: 'var(--space-2)',
          fontSize: 'var(--text-2xl)',
          fontWeight: 'var(--weight-medium)',
          color: tone === 'warning' ? 'var(--warning)' : 'var(--text-primary)',
        }}
      >
        {value}
      </div>
      {hint && (
        <div style={{ marginTop: 'var(--space-1)', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
          {hint}
        </div>
      )}
    </div>
  );
  return to ? (
    <Link to={to} style={{ textDecoration: 'none', color: 'inherit' }}>
      {body}
    </Link>
  ) : (
    body
  );
}

const sectionHeadingStyle: CSSProperties = {
  fontSize: 'var(--text-lg)',
  fontWeight: 'var(--weight-medium)',
  margin: '0 0 var(--space-3)',
};

const cardGridStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
  gap: 'var(--space-3)',
};

function statCardStyle(tone: 'neutral' | 'warning', clickable: boolean): CSSProperties {
  return {
    background: 'var(--bg-surface)',
    border: `1px solid ${tone === 'warning' ? 'var(--warning)' : 'var(--border)'}`,
    borderRadius: 'var(--radius-lg)',
    padding: 'var(--space-4)',
    cursor: clickable ? 'pointer' : 'default',
  };
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
