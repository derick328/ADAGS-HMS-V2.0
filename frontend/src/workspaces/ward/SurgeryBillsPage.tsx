import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchInvoiceById, fetchInvoiceLines, searchInvoices } from '../../shared/api/invoices';
import { fetchActiveSurgeryOrders, type SurgeryOrderResponse } from '../../shared/api/surgeryOrders';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDate, formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

// 17-ward-nurse.md §11: read-only — same shape as Ward Bills, scoped to
// surgical episodes instead of ward admissions.
export function SurgeryBillsPage() {
  const { accessToken } = useAuth();
  const { data: orders, isLoading } = useQuery({
    queryKey: ['surgery-orders', 'active'],
    queryFn: () => fetchActiveSurgeryOrders(accessToken as string),
    enabled: accessToken != null,
  });
  const [selected, setSelected] = useState<SurgeryOrderResponse | null>(null);

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Surgery Bills</h1>
        <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>Read-only</p>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: 'var(--space-4)' }}>
        <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
          {isLoading ? (
            <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>Loading…</p>
          ) : (orders ?? []).length === 0 ? (
            <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No open surgical episodes.</p>
          ) : (
            <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
              {(orders ?? []).map((order) => (
                <li key={order.id}>
                  <button
                    type="button"
                    onClick={() => setSelected(order)}
                    style={{
                      display: 'block',
                      width: '100%',
                      textAlign: 'left',
                      padding: 'var(--space-3)',
                      border: 'none',
                      borderBottom: '1px solid var(--border)',
                      background: selected?.id === order.id ? 'var(--green-100)' : 'transparent',
                      cursor: 'pointer',
                      font: 'inherit',
                    }}
                  >
                    <div style={{ fontWeight: 'var(--weight-medium)' }}>{order.procedureName ?? 'Unreconciled emergency procedure'}</div>
                    <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{formatDate(order.scheduledDate)}</div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          {selected ? <SurgeryInvoices encounterId={selected.encounterId as string} /> : <p style={{ color: 'var(--text-muted)' }}>Select a surgical episode to view its bills.</p>}
        </div>
      </div>
    </div>
  );
}

function SurgeryInvoices({ encounterId }: { encounterId: string }) {
  const { accessToken } = useAuth();
  const { data: invoices, isLoading } = useQuery({
    queryKey: ['invoices', 'search', { encounterId }],
    queryFn: () => searchInvoices({ encounterId }, accessToken as string),
    enabled: accessToken != null,
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
  const { data: invoice } = useQuery({
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
          <div style={{ fontWeight: 'var(--weight-medium)' }}>{invoice?.invoiceNumber ?? invoiceId.slice(0, 8)}</div>
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
            {invoice?.state} · {formatDateTime(invoice?.createdAt)}
          </div>
        </div>
        <div className="money" style={{ fontWeight: 'var(--weight-medium)', fontSize: 'var(--text-lg)' }}>
          {formatMoney(invoice?.outstandingTotalMinor, invoice?.currencyCode)} outstanding
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
