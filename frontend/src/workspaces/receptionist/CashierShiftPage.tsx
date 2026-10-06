import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  closeCashierShift,
  fetchCashierShiftPayments,
  fetchCurrentCashierShift,
  openCashierShift,
  type CashierShiftResponse,
} from '../../shared/api/cashierShifts';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';
import { BOOTSTRAP_FACILITY_ID } from '../../shared/facility';

// 05-receptionist.md §7.1, 02-billing-architecture.md §9.1: the cashier
// declares (counted cash) — reconciliation is a separate, later accountant
// action, not built here (a different actor, per the DB CHECK).
export function CashierShiftPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canOpen = usePermission('CASHIER_SHIFT_OPEN');
  const canClose = usePermission('CASHIER_SHIFT_CLOSE_OWN');
  const [justClosed, setJustClosed] = useState<CashierShiftResponse | null>(null);
  const [countedCash, setCountedCash] = useState('');

  const { data: shift, isLoading } = useQuery({
    queryKey: ['cashier-shift', 'current'],
    queryFn: () => fetchCurrentCashierShift(accessToken as string),
    enabled: accessToken != null,
  });

  const { data: payments } = useQuery({
    queryKey: ['cashier-shift', 'payments', shift?.id],
    queryFn: () => fetchCashierShiftPayments(shift?.id as string, accessToken as string),
    enabled: accessToken != null && shift != null,
  });

  const open = useMutation({
    mutationFn: () => openCashierShift({ facilityId: BOOTSTRAP_FACILITY_ID }, accessToken as string),
    onSuccess: () => {
      setJustClosed(null);
      queryClient.invalidateQueries({ queryKey: ['cashier-shift'] });
    },
  });

  const close = useMutation({
    mutationFn: () =>
      closeCashierShift(shift?.id as string, { countedCashMinor: Math.round(Number(countedCash) * 100) }, accessToken as string),
    onSuccess: (closed) => {
      setJustClosed(closed);
      setCountedCash('');
      queryClient.invalidateQueries({ queryKey: ['cashier-shift'] });
    },
  });

  const totalsByMethod = new Map<string, number>();
  for (const p of payments ?? []) {
    if (p.status !== 'SETTLED') continue;
    const method = p.method ?? 'UNKNOWN';
    totalsByMethod.set(method, (totalsByMethod.get(method) ?? 0) + (p.amountMinor ?? 0));
  }
  const grandTotalMinor = [...totalsByMethod.values()].reduce((a, b) => a + b, 0);

  return (
    <div>
      <PageHeader title={<>Cashier shift</>} description={<>Review collections and reconcile your current cashier shift.</>} />

      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : !shift ? (
        <div style={cardStyle}>
          {justClosed ? (
            <ClosedShiftSummary shift={justClosed} />
          ) : (
            <FeedbackState>No cashier shift open.</FeedbackState>
          )}
          {canOpen && (
            <button type="button" className="btn btn-primary" style={{ marginTop: 'var(--space-3)' }} disabled={open.isPending} onClick={() => open.mutate()}>
              {open.isPending ? 'Opening…' : 'Open shift'}
            </button>
          )}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div style={cardStyle}>
            <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Opened {formatDateTime(shift.openedAt)}</p>
            <p className="tabular" style={{ margin: 'var(--space-1) 0 0', fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }}>
              {formatMoney(grandTotalMinor, 'TZS')}
            </p>
            <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>Running total, this shift</p>
          </div>

          <section style={sectionStyle}>
            <h2 style={sectionTitleStyle}>By method</h2>
            {totalsByMethod.size === 0 ? (
              <FeedbackState>No payments recorded yet.</FeedbackState>
            ) : (
              <TableRegion label="Totals by payment method"><table className="table">
                <caption className="sr-only">Totals by payment method</caption>
                <thead><tr><th>Method</th><th>Total</th></tr></thead>
                <tbody>
                  {[...totalsByMethod.entries()].map(([method, minor]) => (
                    <tr key={method}>
                      <td>{method.replaceAll('_', ' ').toLowerCase()}</td>
                      <td className="money">{formatMoney(minor, 'TZS')}</td>
                    </tr>
                  ))}
                </tbody>
              </table></TableRegion>
            )}
          </section>

          {canClose && (
            <section style={{ ...cardStyle, maxWidth: 400 }}>
              <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Close shift</h2>
              <label htmlFor="counted-cash" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
                Counted cash (TZS)
              </label>
              <input className="clinical-control"
                id="counted-cash"
                type="number"
                min={0}
                value={countedCash}
                onChange={(e) => setCountedCash(e.target.value)}
                style={{ width: '100%', marginTop: 'var(--space-1)', padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
              />
              {close.error && <div className="form-error">Something went wrong closing the shift.</div>}
              <button
                type="button"
                className="btn btn-danger"
                style={{ marginTop: 'var(--space-3)' }}
                disabled={countedCash.trim() === '' || close.isPending}
                onClick={() => close.mutate()}
              >
                {close.isPending ? 'Closing…' : 'Close shift'}
              </button>
              <p style={{ marginTop: 'var(--space-2)', fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
                The system computes what was expected and records the variance. An accountant reconciles separately.
              </p>
            </section>
          )}
        </div>
      )}
    </div>
  );
}

function ClosedShiftSummary({ shift }: { shift: CashierShiftResponse }) {
  const varianceMinor = shift.varianceMinor ?? 0;
  return (
    <div>
      <p style={{ margin: 0, fontWeight: 'var(--weight-medium)' }}>Shift closed.</p>
      <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 'var(--space-2)' }}>
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Expected</dt>
        <dd style={{ margin: '0 0 var(--space-2)' }}>{formatMoney(shift.expectedCashMinor, 'TZS')}</dd>
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Counted</dt>
        <dd style={{ margin: '0 0 var(--space-2)' }}>{formatMoney(shift.countedCashMinor, 'TZS')}</dd>
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Variance</dt>
        <dd style={{ margin: 0, color: varianceMinor !== 0 ? 'var(--warning)' : 'inherit' }}>{formatMoney(varianceMinor, 'TZS')}</dd>
      </dl>
    </div>
  );
}

const cardStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
} as const;

const sectionStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  overflow: 'hidden',
} as const;

const sectionTitleStyle = {
  fontSize: 'var(--text-lg)',
  fontWeight: 'var(--weight-medium)',
  margin: 0,
  padding: 'var(--space-4) var(--space-4) 0',
} as const;
