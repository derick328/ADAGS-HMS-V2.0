import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchInvoiceById, fetchInvoiceLines, searchInvoices } from '../../shared/api/invoices';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';
import { useWardContext } from './WardContext';
import { useWardPatients } from './useWardPatients';
import type { WardPatientRowResponse } from '../../shared/api/admissions';

// 17-ward-nurse.md §11: read-only — the nurse views, receptionist collects.
// Accrued bed-day charges surface here because the nightly accrual job
// posts them straight onto the encounter's invoice as ordinary lines
// (04-procedure-bedday-rules.md §3.5) — nothing ward-specific to separate out.
export function WardBillsPage() {
  const { wardName } = useWardContext();
  const { data: patients, isLoading } = useWardPatients();
  const [selected, setSelected] = useState<WardPatientRowResponse | null>(null);

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Ward Bills</h1>
        <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>{wardName ?? 'Your ward'} — read-only</p>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 'var(--space-4)' }}>
        <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
          {isLoading ? (
            <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>Loading…</p>
          ) : (patients ?? []).length === 0 ? (
            <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No patients on this ward.</p>
          ) : (
            <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
              {(patients ?? []).map((row) => (
                <li key={row.admissionId}>
                  <button
                    type="button"
                    onClick={() => setSelected(row)}
                    style={{
                      display: 'block',
                      width: '100%',
                      textAlign: 'left',
                      padding: 'var(--space-3)',
                      border: 'none',
                      borderBottom: '1px solid var(--border)',
                      background: selected?.admissionId === row.admissionId ? 'var(--green-100)' : 'transparent',
                      cursor: 'pointer',
                      font: 'inherit',
                    }}
                  >
                    <div style={{ fontWeight: 'var(--weight-medium)' }}>{row.patientName ?? 'Unknown patient'}</div>
                    <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{row.bedLabel ?? '—'}</div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>{selected ? <PatientBills row={selected} /> : <p style={{ color: 'var(--text-muted)' }}>Select a patient to view their bills.</p>}</div>
      </div>
    </div>
  );
}

function PatientBills({ row }: { row: WardPatientRowResponse }) {
  const { accessToken } = useAuth();
  const { data: invoices, isLoading } = useQuery({
    queryKey: ['invoices', 'search', { encounterId: row.encounterId }],
    queryFn: () => searchInvoices({ encounterId: row.encounterId as string }, accessToken as string),
    enabled: accessToken != null && row.encounterId != null,
  });

  if (isLoading) return <p style={{ color: 'var(--text-muted)' }}>Loading…</p>;
  if (!invoices || invoices.length === 0) return <p style={{ color: 'var(--text-muted)' }}>No invoices for this encounter yet.</p>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      {invoices.map((invoice) => (
        <InvoiceCard key={invoice.id} invoiceId={invoice.id as string} />
      ))}
    </div>
  );
}

function InvoiceCard({ invoiceId }: { invoiceId: string }) {
  const { accessToken } = useAuth();
  const { data: invoices } = useQuery({
    queryKey: ['invoices', invoiceId],
    queryFn: () => fetchInvoiceById(invoiceId, accessToken as string),
    enabled: accessToken != null,
  });
  const { data: lines } = useQuery({
    queryKey: ['invoices', invoiceId, 'lines'],
    queryFn: () => fetchInvoiceLines(invoiceId, accessToken as string),
    enabled: accessToken != null,
  });

  return (
    <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <div style={{ padding: 'var(--space-4)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontWeight: 'var(--weight-medium)' }}>{invoices?.invoiceNumber ?? invoiceId.slice(0, 8)}</div>
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
            {invoices?.state} · {formatDateTime(invoices?.createdAt)}
          </div>
        </div>
        <div className="money" style={{ fontWeight: 'var(--weight-medium)', fontSize: 'var(--text-lg)' }}>
          {formatMoney(invoices?.outstandingTotalMinor, invoices?.currencyCode)} outstanding
        </div>
      </div>
      <table className="table">
        <caption className="sr-only">Invoice lines</caption>
        <thead>
          <tr>
            <th>Item</th>
            <th>Qty</th>
            <th className="money">Amount</th>
            <th className="money">Remaining</th>
          </tr>
        </thead>
        <tbody>
          {(lines ?? []).map((line) => (
            <tr key={line.id}>
              <td>{line.serviceItemName}</td>
              <td>{line.quantity}</td>
              <td className="money">{formatMoney(line.grossAmountMinor, line.currencyCode)}</td>
              <td className="money">{formatMoney(line.remainingMinor, line.currencyCode)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
