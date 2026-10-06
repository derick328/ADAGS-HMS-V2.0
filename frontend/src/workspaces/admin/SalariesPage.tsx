import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchPayrollPreview } from '../../shared/api/payroll';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';
import { ExpensesCategoryPage } from './ExpensesCategoryPage';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

// 18-admin.md §5 / §8: the Salaries expense category is the same generic
// finance/expenses ledger as every other category (ad-hoc allowances,
// bonuses, one-off payments), but the *base salary* every employee is
// owed comes from the payroll module, not the ledger. Showing only the
// ad-hoc rows would hide 95% of what an admin looks at this screen for.
// So this page stacks: the current-month payroll roll (every employee,
// one row) on top, followed by the generic expense ledger below.
export function SalariesPage() {
  const { accessToken } = useAuth();
  const canReadPayroll = usePermission('PAYROLL_MANAGE');
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());

  const { data: preview, isLoading, isError } = useQuery({
    queryKey: ['payroll', 'preview', month, year],
    queryFn: () => fetchPayrollPreview(month, year, accessToken as string),
    enabled: accessToken != null && canReadPayroll,
  });

  const currency = preview?.currencyCode ?? 'TZS';
  const lines = preview?.lines ?? [];

  return (
    <div>
      {canReadPayroll && (
        <section style={{ marginBottom: 'var(--space-6)' }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              gap: 'var(--space-3)',
              flexWrap: 'wrap',
              marginBottom: 'var(--space-3)',
            }}
          >
            <div>
              <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', margin: 0 }}>
                Employees — {MONTH_NAMES[month - 1]} {year}
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
                Every active staff row that would be paid this period. Generated from the payroll preview — no
                batch is written until you disburse.
              </p>
            </div>
            <PeriodPicker month={month} year={year} onChange={(m, y) => { setMonth(m); setYear(y); }} />
          </div>

          {preview && (
            <div className="clinical-grid"
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                gap: 'var(--space-3)',
                marginBottom: 'var(--space-3)',
              }}
            >
              <SummaryCard label="Headcount" value={String(preview.headcount ?? 0)} />
              <SummaryCard
                label="Gross"
                value={formatMoney(preview.grossTotalMinor, currency)}
                money
              />
              <SummaryCard
                label="Deductions"
                value={formatMoney(preview.deductionsTotalMinor, currency)}
                money
              />
              <SummaryCard
                label="Net payable"
                value={formatMoney(preview.netTotalMinor, currency)}
                money
              />
            </div>
          )}

          <div
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
            }}
          >
            {isLoading ? (
              <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>
                Loading employees…
              </div>
            ) : isError ? (
              <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>
                Couldn't load the payroll preview.
              </div>
            ) : lines.length === 0 ? (
              <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>
                No active employees with a basic salary set. Add employees under Users first.
              </div>
            ) : (
              <TableRegion><table className="table">
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th style={{ textAlign: 'right' }}>Gross</th>
                    <th style={{ textAlign: 'right' }}>Deductions</th>
                    <th style={{ textAlign: 'right' }}>Net</th>
                  </tr>
                </thead>
                <tbody>
                  {lines
                    .slice()
                    .sort((a, b) => (a.fullName ?? '').localeCompare(b.fullName ?? ''))
                    .map((line) => (
                      <tr key={line.staffId}>
                        <td style={{ fontWeight: 'var(--weight-medium)' }}>{line.fullName ?? '—'}</td>
                        <td className="money" style={{ textAlign: 'right' }}>
                          {formatMoney(line.grossMinor, currency)}
                        </td>
                        <td className="money" style={{ textAlign: 'right' }}>
                          {formatMoney(line.deductionsMinor, currency)}
                        </td>
                        <td className="money" style={{ textAlign: 'right' }}>
                          {formatMoney(line.netMinor, currency)}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table></TableRegion>
            )}
          </div>
        </section>
      )}

      <section>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', margin: '0 0 var(--space-3)' }}>
          One-off salary expenses
        </h2>
        <ExpensesCategoryPage category="Salaries" />
      </section>
    </div>
  );
}

function PeriodPicker({
  month,
  year,
  onChange,
}: {
  month: number;
  year: number;
  onChange: (month: number, year: number) => void;
}) {
  return (
    <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-end' }}>
      <div>
        <label htmlFor="payroll-month" style={{ display: 'block', fontSize: 'var(--text-xs)' }}>
          Month
        </label>
        <select className="clinical-control"
          id="payroll-month"
          value={month}
          onChange={(e) => onChange(Number(e.target.value), year)}
          style={fieldStyle}
        >
          {MONTH_NAMES.map((name, index) => (
            <option key={name} value={index + 1}>
              {name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="payroll-year" style={{ display: 'block', fontSize: 'var(--text-xs)' }}>
          Year
        </label>
        <input className="clinical-control"
          id="payroll-year"
          type="number"
          value={year}
          onChange={(e) => onChange(month, Number(e.target.value))}
          min={2020}
          max={2100}
          style={{ ...fieldStyle, width: 90 }}
        />
      </div>
    </div>
  );
}

function SummaryCard({ label, value, money = false }: { label: string; value: string; money?: boolean }) {
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-3) var(--space-4)',
      }}
    >
      <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-secondary)' }}>{label}</div>
      <div
        className={money ? 'money' : undefined}
        style={{ marginTop: 4, fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}
      >
        {value}
      </div>
    </div>
  );
}

const fieldStyle = {
  padding: 'var(--space-1) var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
  fontSize: 'var(--text-sm)',
} as const;
