import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { fetchExpensesByCategory, fetchExpensesMonthlyTrend } from '../../shared/api/finance';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatMoney } from '../../shared/format/money';

const MONTH_NAMES = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

function defaultFrom(): string {
  const d = new Date();
  d.setMonth(d.getMonth() - 11, 1);
  return d.toISOString().slice(0, 10);
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10);
}

// 18-admin.md §10: reads over the existing expense ledger — no new table.
// Deliberately does NOT show a revenue or profit figure here: §10's own
// corrections (deposits are liabilities, insurance receivables aren't
// cash, emergency-bypass debt is neither revenue nor a receivable yet)
// mean composing an expense total against a revenue total on this screen
// would misrepresent the numbers. This is spend only.
export function ExpenseAnalyticsPage() {
  const { accessToken } = useAuth();
  const [from, setFrom] = useState(defaultFrom());
  const [to, setTo] = useState(defaultTo());

  const { data: byCategory, isLoading: loadingCategory } = useQuery({
    queryKey: ['expense-analytics', 'by-category', from, to],
    queryFn: () => fetchExpensesByCategory(from, to, accessToken as string),
    enabled: accessToken != null,
  });

  const { data: monthlyTrend, isLoading: loadingTrend } = useQuery({
    queryKey: ['expense-analytics', 'monthly-trend', from, to],
    queryFn: () => fetchExpensesMonthlyTrend(from, to, accessToken as string),
    enabled: accessToken != null,
  });

  const maxCategory = Math.max(1, ...(byCategory ?? []).map((c) => c.totalMinor ?? 0));
  const maxMonth = Math.max(1, ...(monthlyTrend ?? []).map((m) => m.totalMinor ?? 0));

  return (
    <div>
      <header style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Expense analytics</h1>
      </header>

      <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-end', marginBottom: 'var(--space-5)' }}>
        <div>
          <label htmlFor="analytics-from" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
            From
          </label>
          <input
            id="analytics-from"
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            style={{ padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)' }}
          />
        </div>
        <div>
          <label htmlFor="analytics-to" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
            To
          </label>
          <input
            id="analytics-to"
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            style={{ padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)' }}
          />
        </div>
      </div>

      <section style={{ marginBottom: 'var(--space-8)' }}>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}>By category</h2>
        <AnalyticsTable
          isLoading={loadingCategory}
          isEmpty={(byCategory ?? []).length === 0}
          emptyMessage="No expenses recorded in this range"
        >
          {(byCategory ?? [])
            .slice()
            .sort((a, b) => (b.totalMinor ?? 0) - (a.totalMinor ?? 0))
            .map((row) => (
              <tr key={row.category}>
                <td>{row.category}</td>
                <td>
                  <BarCell value={row.totalMinor ?? 0} max={maxCategory} />
                </td>
                <td className="money">{formatMoney(row.totalMinor, row.currencyCode)}</td>
              </tr>
            ))}
        </AnalyticsTable>
      </section>

      <section>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}>Monthly trend</h2>
        <AnalyticsTable
          isLoading={loadingTrend}
          isEmpty={(monthlyTrend ?? []).length === 0}
          emptyMessage="No expenses recorded in this range"
        >
          {(monthlyTrend ?? [])
            .slice()
            .sort((a, b) => (a.year ?? 0) - (b.year ?? 0) || (a.month ?? 0) - (b.month ?? 0))
            .map((row) => (
              <tr key={`${row.year}-${row.month}`}>
                <td>
                  {MONTH_NAMES[(row.month ?? 1) - 1]} {row.year}
                </td>
                <td>
                  <BarCell value={row.totalMinor ?? 0} max={maxMonth} />
                </td>
                <td className="money">{formatMoney(row.totalMinor, row.currencyCode)}</td>
              </tr>
            ))}
        </AnalyticsTable>
      </section>
    </div>
  );
}

function AnalyticsTable({
  isLoading,
  isEmpty,
  emptyMessage,
  children,
}: {
  isLoading: boolean;
  isEmpty: boolean;
  emptyMessage: string;
  children: ReactNode;
}) {
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
      }}
    >
      {isLoading ? (
        <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>Loading…</div>
      ) : isEmpty ? (
        <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>{emptyMessage}</div>
      ) : (
        <table className="table">
          <tbody>{children}</tbody>
        </table>
      )}
    </div>
  );
}

function BarCell({ value, max }: { value: number; max: number }) {
  const percent = Math.max(2, Math.round((value / max) * 100));
  return (
    <div style={{ background: 'var(--gray-100)', borderRadius: 'var(--radius-sm)', height: 8, width: 160 }}>
      <div
        style={{
          background: 'var(--info)',
          borderRadius: 'var(--radius-sm)',
          height: 8,
          width: `${percent}%`,
        }}
      />
    </div>
  );
}
