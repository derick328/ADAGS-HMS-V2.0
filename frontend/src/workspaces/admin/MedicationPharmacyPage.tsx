import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { fetchRestocks, type RestockLedgerEntry } from '../../shared/api/stockBatches';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';
import { ExpensesCategoryPage } from './ExpensesCategoryPage';

// 18-admin.md §8 "Medication & pharmacy": the admin doesn't retype
// pharmacy spend the pharmacist has already recorded on the receive
// form — every restock lands here as a spend line derived from
// stock_batch + the RECEIPT stock_movement. Ad-hoc medication-and-pharmacy
// spend outside a formal restock (delivery courier fees, one-off drug
// samples) still flows through the generic expense ledger below.
export function MedicationPharmacyPage() {
  const { accessToken } = useAuth();
  const canRead = usePermission('STOCK_READ');
  const [rangePreset, setRangePreset] = useState<'MTD' | 'LAST_30' | 'LAST_90'>('MTD');
  const range = useMemo(() => resolveRange(rangePreset), [rangePreset]);

  const { data: restocks, isLoading, isError } = useQuery({
    queryKey: ['stock-batches', 'restocks', range.from, range.to],
    queryFn: () => fetchRestocks(range.from, range.to, accessToken as string),
    enabled: accessToken != null && canRead,
  });

  const rows = restocks ?? [];
  const currency = rows.find((r) => r.currencyCode)?.currencyCode ?? 'TZS';
  const totalSpend = rows.reduce((sum, r) => sum + (r.totalCostMinor ?? 0), 0);

  return (
    <div>
      {canRead && (
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
                Pharmacy restocks
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
                Every restock the pharmacist recorded in this window. Total cost is unit cost × received quantity.
              </p>
            </div>
            <RangeTabs value={rangePreset} onChange={setRangePreset} />
          </div>

          <div
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
            }}
          >
            {isLoading ? (
              <Empty message="Loading pharmacy restocks…" />
            ) : isError ? (
              <Empty message="Couldn't load pharmacy restocks." />
            ) : rows.length === 0 ? (
              <Empty message="No pharmacist restocks in this window yet." />
            ) : (
              <RestockTable rows={rows} currency={currency} totalSpend={totalSpend} />
            )}
          </div>
        </section>
      )}

      <section>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', margin: '0 0 var(--space-3)' }}>
          Other medication & pharmacy expenses
        </h2>
        <ExpensesCategoryPage category="Medication & pharmacy" />
      </section>
    </div>
  );
}

function RestockTable({
  rows,
  currency,
  totalSpend,
}: {
  rows: RestockLedgerEntry[];
  currency: string;
  totalSpend: number;
}) {
  return (
    <>
      <table className="table">
        <thead>
          <tr>
            <th>Received</th>
            <th>Item</th>
            <th>Batch</th>
            <th>Supplier</th>
            <th>Recorded by</th>
            <th style={{ textAlign: 'right' }}>Qty</th>
            <th style={{ textAlign: 'right' }}>Unit cost</th>
            <th style={{ textAlign: 'right' }}>Total</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.batchId}>
              <td>{formatReceivedAt(r.receivedAt)}</td>
              <td style={{ fontWeight: 'var(--weight-medium)' }}>{r.itemName}</td>
              <td title={r.deliveryNoteRef}>{r.batchNumber}</td>
              <td>{r.supplierName}</td>
              <td>{r.receivedByName ?? '—'}</td>
              <td style={{ textAlign: 'right' }}>{r.receivedQuantity}</td>
              <td className="money" style={{ textAlign: 'right' }}>
                {formatMoney(r.unitCostAmountMinor, r.currencyCode)}
              </td>
              <td className="money" style={{ textAlign: 'right' }}>
                {formatMoney(r.totalCostMinor, r.currencyCode)}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={7} style={{ textAlign: 'right', fontWeight: 'var(--weight-medium)' }}>
              Total spent
            </td>
            <td className="money" style={{ textAlign: 'right', fontWeight: 'var(--weight-medium)' }}>
              {formatMoney(totalSpend, currency)}
            </td>
          </tr>
        </tfoot>
      </table>
    </>
  );
}

function RangeTabs({
  value,
  onChange,
}: {
  value: 'MTD' | 'LAST_30' | 'LAST_90';
  onChange: (v: 'MTD' | 'LAST_30' | 'LAST_90') => void;
}) {
  const options: { key: 'MTD' | 'LAST_30' | 'LAST_90'; label: string }[] = [
    { key: 'MTD', label: 'This month' },
    { key: 'LAST_30', label: 'Last 30 days' },
    { key: 'LAST_90', label: 'Last 90 days' },
  ];
  return (
    <div style={{ display: 'flex', gap: 'var(--space-1)' }}>
      {options.map((option) => (
        <button
          key={option.key}
          type="button"
          onClick={() => onChange(option.key)}
          className={value === option.key ? 'btn btn-secondary' : 'btn btn-ghost'}
          style={{ fontSize: 'var(--text-sm)' }}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function Empty({ message }: { message: string }) {
  return (
    <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>{message}</div>
  );
}

function resolveRange(preset: 'MTD' | 'LAST_30' | 'LAST_90'): { from: string; to: string } {
  const now = new Date();
  const to = toIsoDate(now);
  if (preset === 'MTD') {
    return { from: toIsoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to };
  }
  const back = new Date(now);
  back.setDate(back.getDate() - (preset === 'LAST_30' ? 29 : 89));
  return { from: toIsoDate(back), to };
}

function toIsoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function formatReceivedAt(value: string | undefined): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', {
    dateStyle: 'medium',
    timeZone: 'Africa/Dar_es_Salaam',
  }).format(new Date(value));
}
