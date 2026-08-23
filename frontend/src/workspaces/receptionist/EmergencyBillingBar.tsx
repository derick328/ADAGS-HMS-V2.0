import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchActivePaymentBypasses } from '../../shared/api/paymentBypasses';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatMoney } from '../../shared/format/money';
import { formatDateTime } from '../../shared/format/date';

// 02-billing-architecture.md §5.4: persistent, hidden when empty. Two
// constraints that must survive: never blocks discharge (this bar has no
// action that could — it only ever offers to collect payment), and no
// payment-prompt nudge during active critical care.
//
// "Current location" is omitted — it depends on EncounterActivity
// (05a-receptionist-decisions.md §5), not built. For the same reason there
// is no signal here to tell a stable bypass patient from one still in
// resuscitation, so the "Take payment" action is left out entirely rather
// than guessed at — showing it unconditionally would risk exactly the
// nudge-during-critical-care the spec forbids. A receptionist who judges it
// safe can still act from the Billing screen directly.
export function EmergencyBillingBar() {
  const { accessToken } = useAuth();
  const [expanded, setExpanded] = useState(false);

  const { data: bypasses } = useQuery({
    queryKey: ['payment-bypasses', 'active'],
    queryFn: () => fetchActivePaymentBypasses(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  if (!bypasses || bypasses.length === 0) {
    return null;
  }

  const totalOutstandingMinor = bypasses.reduce((sum, b) => sum + (b.outstandingTotalMinor ?? 0), 0);
  const currencyCode = bypasses[0]?.currencyCode;

  return (
    <div style={{ background: 'var(--warning-bg)', borderBottom: '1px solid var(--warning)' }}>
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-2)',
          padding: 'var(--space-2) var(--space-4)',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          font: 'inherit',
          color: 'var(--warning)',
          fontWeight: 'var(--weight-medium)',
          textAlign: 'left',
        }}
      >
        <span aria-hidden="true">⚠</span>
        <span>
          EMERGENCY — {bypasses.length} {bypasses.length === 1 ? 'patient' : 'patients'} ·{' '}
          {formatMoney(totalOutstandingMinor, currencyCode)} outstanding
        </span>
        <span style={{ marginLeft: 'auto', fontSize: 'var(--text-sm)' }}>{expanded ? 'Hide' : 'Expand'}</span>
      </button>

      {expanded && (
        <table className="table" style={{ background: 'var(--bg-surface)' }}>
          <caption className="sr-only">Patients under an active payment bypass</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Bypass declared</th>
              <th>Reason</th>
              <th>Accrued</th>
            </tr>
          </thead>
          <tbody>
            {bypasses.map((bypass) => (
              <tr key={bypass.paymentBypassId}>
                <td style={{ fontWeight: 'var(--weight-medium)' }}>{bypass.patientName ?? bypass.hospitalNumber ?? '—'}</td>
                <td>{formatDateTime(bypass.grantedAt)}</td>
                <td>{bypass.reason}</td>
                <td className="money">{formatMoney(bypass.outstandingTotalMinor, bypass.currencyCode)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
