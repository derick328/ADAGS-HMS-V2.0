import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Combobox } from '../../shared/components/Combobox';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { restock } from '../../shared/api/stockBatches';
import { fetchCurrentStock, fetchExpiringBatches, fetchLowStock, fetchStockValuation } from '../../shared/api/stockReports';
import { fetchMyPharmacyStore } from '../../shared/api/stores';
import { fetchSuppliers, type SupplierResponse } from '../../shared/api/suppliers';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDate } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

type Tab = 'current' | 'low' | 'expiring' | 'valuation';

const TABS: { key: Tab; label: string }[] = [
  { key: 'current', label: 'Current stock' },
  { key: 'low', label: 'Low stock' },
  { key: 'expiring', label: 'Expiring (90d)' },
  { key: 'valuation', label: 'Valuation' },
];

// 12-pharmacist.md §7: current stock / low-stock alerts / batches expiring
// within 90 days / stock valuation, plus the restock form (§7.2).
export function StockPage() {
  const [tab, setTab] = useState<Tab>('current');

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Stock</h1>
      </header>

      <RestockForm />

      <nav aria-label="Stock views" style={{ display: 'flex', gap: 'var(--space-1)', borderBottom: '1px solid var(--border)', marginBottom: 'var(--space-4)' }}>
        {TABS.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            aria-current={tab === key ? 'page' : undefined}
            style={{
              padding: 'var(--space-2) var(--space-3)',
              border: 'none',
              background: 'none',
              font: 'inherit',
              cursor: 'pointer',
              color: tab === key ? 'var(--text-primary)' : 'var(--text-muted)',
              fontWeight: tab === key ? 'var(--weight-medium)' : 'var(--weight-normal)',
              boxShadow: tab === key ? 'inset 0 -2px 0 var(--green-signal)' : 'none',
            }}
          >
            {label}
          </button>
        ))}
      </nav>

      {tab === 'current' && <CurrentStockView />}
      {tab === 'low' && <LowStockView />}
      {tab === 'expiring' && <ExpiringView />}
      {tab === 'valuation' && <ValuationView />}
    </div>
  );
}

function CurrentStockView() {
  const { accessToken } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['stock-reports', 'current-stock'],
    queryFn: () => fetchCurrentStock(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <TableShell isLoading={isLoading} isEmpty={rows.length === 0} emptyMessage="No stock on hand.">
      <thead>
        <tr>
          <th>Item</th>
          <th>Batch</th>
          <th>Expiry</th>
          <th>Store</th>
          <th>Quantity</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            <td>{r.itemName} <span style={{ color: 'var(--text-muted)' }}>· {r.itemCode}</span></td>
            <td style={{ fontFamily: 'var(--font-mono)' }}>{r.batchNumber}</td>
            <td className="tabular">{formatDate(r.expiryDate)}</td>
            <td>{r.storeName}</td>
            <td className="tabular">{r.quantity}</td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  );
}

function LowStockView() {
  const { accessToken } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['stock-reports', 'low-stock'],
    queryFn: () => fetchLowStock(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <TableShell isLoading={isLoading} isEmpty={rows.length === 0} emptyMessage="Nothing is below its reorder level.">
      <thead>
        <tr>
          <th>Item</th>
          <th>On hand</th>
          <th>Reorder level</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="row-warning">
            <td>{r.itemName} <span style={{ color: 'var(--text-muted)' }}>· {r.itemCode}</span></td>
            <td className="tabular">{r.totalQuantity}</td>
            <td className="tabular">{r.reorderLevel}</td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  );
}

function ExpiringView() {
  const { accessToken } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['stock-reports', 'expiring-batches'],
    queryFn: () => fetchExpiringBatches(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <TableShell isLoading={isLoading} isEmpty={rows.length === 0} emptyMessage="Nothing expiring within 90 days.">
      <thead>
        <tr>
          <th>Item</th>
          <th>Batch</th>
          <th>Expiry</th>
          <th>Quantity</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="row-warning">
            <td>{r.itemName} <span style={{ color: 'var(--text-muted)' }}>· {r.itemCode}</span></td>
            <td style={{ fontFamily: 'var(--font-mono)' }}>{r.batchNumber}</td>
            <td className="tabular">{formatDate(r.expiryDate)}</td>
            <td className="tabular">{r.quantity}</td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  );
}

function ValuationView() {
  const { accessToken } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['stock-reports', 'valuation'],
    queryFn: () => fetchStockValuation(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <TableShell isLoading={isLoading} isEmpty={rows.length === 0} emptyMessage="No valuation to show.">
      <thead>
        <tr>
          <th>Store</th>
          <th>Total value</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            <td>{r.storeName}</td>
            <td className="tabular">{formatMoney(r.totalValueAmountMinor, r.currencyCode)}</td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  );
}

function TableShell({ isLoading, isEmpty, emptyMessage, children }: { isLoading: boolean; isEmpty: boolean; emptyMessage: string; children: React.ReactNode }) {
  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden', marginBottom: 'var(--space-6)' }}>
      {isLoading ? (
        <p style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>Loading…</p>
      ) : isEmpty ? (
        <p style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>{emptyMessage}</p>
      ) : (
        <table className="table">
          <caption className="sr-only">Stock report</caption>
          {children}
        </table>
      )}
    </div>
  );
}

// 12-pharmacist.md §7.2 (D3, D6): batchNumber, unitCost, and
// deliveryNoteRef are all required — the minimum control on stock
// creation. Supplier is a managed list, never free text.
const restockSchema = z.object({
  itemId: z.string().min(1, 'Select an item'),
  batchNumber: z.string().min(1, 'Enter the batch number'),
  expiryDate: z.string().min(1, 'Enter the expiry date'),
  quantity: z.coerce.number({ invalid_type_error: 'Enter a quantity' }).int().positive('Enter a quantity'),
  unitCostAmountMinor: z.coerce.number({ invalid_type_error: 'Enter the unit cost' }).min(0, 'Enter the unit cost'),
  supplierId: z.string().min(1, 'Select a supplier'),
  deliveryNoteRef: z.string().min(1, 'Enter the delivery note reference'),
  notes: z.string().optional(),
});
type RestockValues = z.infer<typeof restockSchema>;

function RestockForm() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [selectedItem, setSelectedItem] = useState<ItemSearchResultResponse | null>(null);
  const [open, setOpen] = useState(false);

  const { data: store } = useQuery({
    queryKey: ['stores', 'mine-pharmacy'],
    queryFn: () => fetchMyPharmacyStore(accessToken as string),
    enabled: accessToken != null,
  });
  const { data: suppliers } = useQuery({
    queryKey: ['suppliers'],
    queryFn: () => fetchSuppliers(accessToken as string),
    enabled: accessToken != null,
  });

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<RestockValues>({ resolver: zodResolver(restockSchema) });

  const submit = useMutation({
    mutationFn: (values: RestockValues) =>
      restock(
        {
          itemId: values.itemId,
          storeId: store?.id as string,
          batchNumber: values.batchNumber,
          expiryDate: values.expiryDate,
          quantity: values.quantity,
          unitCostAmountMinor: values.unitCostAmountMinor,
          supplierId: values.supplierId,
          deliveryNoteRef: values.deliveryNoteRef,
          notes: values.notes || undefined,
        },
        accessToken as string,
      ),
    onSuccess: () => {
      reset();
      setSelectedItem(null);
      queryClient.invalidateQueries({ queryKey: ['stock-reports'] });
    },
  });

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-6)', maxWidth: 640 }}>
      <button type="button" className="btn btn-secondary" onClick={() => setOpen((o) => !o)}>
        {open ? 'Close restock form' : 'Restock'}
      </button>

      {open && (
        <form onSubmit={handleSubmit((v) => submit.mutate(v))} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', marginTop: 'var(--space-4)' }}>
          <div>
            <Combobox<ItemSearchResultResponse>
              label="Item"
              placeholder="Search the formulary"
              search={(q) => searchItems(q, accessToken as string)}
              getKey={(i) => i.id as string}
              renderOption={(i) => (
                <span style={{ opacity: i.selectable === false ? 0.5 : 1 }}>
                  {i.name} {i.strength && <span style={{ color: 'var(--text-muted)' }}>· {i.strength}</span>}
                  {i.selectable === false && <span style={{ color: 'var(--critical)' }}> — {i.unselectableReason}</span>}
                </span>
              )}
              onSelect={(i) => {
                if (i.selectable === false) return;
                setSelectedItem(i);
                setValue('itemId', i.id as string, { shouldValidate: true });
              }}
              clearOnSelect={false}
            />
            {selectedItem && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>Selected: {selectedItem.name}</div>}
            {errors.itemId && <span className="field-error">{errors.itemId.message}</span>}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
            <Field label="Batch number" error={errors.batchNumber?.message}>
              <input {...register('batchNumber')} style={inputStyle} />
            </Field>
            <Field label="Expiry date" error={errors.expiryDate?.message}>
              <input type="date" {...register('expiryDate')} style={inputStyle} />
            </Field>
            <Field label="Quantity" error={errors.quantity?.message}>
              <input type="text" inputMode="numeric" {...register('quantity')} style={inputStyle} />
            </Field>
            <Field label="Unit cost (TZS minor units)" error={errors.unitCostAmountMinor?.message}>
              <input type="text" inputMode="numeric" {...register('unitCostAmountMinor')} style={inputStyle} />
            </Field>
            <Field label="Supplier" error={errors.supplierId?.message}>
              <select {...register('supplierId')} style={inputStyle}>
                <option value="">Select a supplier</option>
                {(suppliers ?? []).map((s: SupplierResponse) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Delivery note reference" error={errors.deliveryNoteRef?.message}>
              <input {...register('deliveryNoteRef')} style={inputStyle} />
            </Field>
          </div>

          <Field label="Notes (optional)">
            <textarea rows={2} {...register('notes')} style={inputStyle} />
          </Field>

          {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
          {submit.isSuccess && <div style={{ color: 'var(--green-600)', fontSize: 'var(--text-sm)' }}>Batch received.</div>}

          <button type="submit" className="btn btn-primary" disabled={submit.isPending || !store} style={{ alignSelf: 'flex-start' }}>
            {submit.isPending ? 'Submitting…' : 'Receive stock'}
          </button>
        </form>
      )}
    </div>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>{label}</label>
      {children}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
