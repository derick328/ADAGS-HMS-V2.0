import { Tabs } from '../../shared/components/Tabs';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { Combobox } from '../../shared/components/Combobox';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { restock } from '../../shared/api/stockBatches';
import { fetchCurrentStock, fetchExpiringBatches, fetchLowStock, fetchStockValuation } from '../../shared/api/stockReports';
import { fetchMyPharmacyStore } from '../../shared/api/stores';
import { createSupplier, fetchSuppliers, type SupplierResponse } from '../../shared/api/suppliers';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDate } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

const OTHER_SUPPLIER_VALUE = '__other__';
function todayInFacilityTz(): string {
  // The backend rejects a past expiry (V23 restock check) and there's no
  // reason a pharmacist would ever intentionally receive expired stock —
  // block it at the input, not just at the API. Uses today in the
  // browser's local zone; the facility runs on Africa/Dar_es_Salaam and
  // the pharmacist is on-site, so browser-local matches facility-local.
  return new Date().toISOString().slice(0, 10);
}

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
      <PageHeader title={<>Stock</>} description={<>Review available batches, expiry dates, and incoming stock.</>} />

      <RestockForm />

      <Tabs aria-label="Stock views" variant="underline" value={tab} onChange={(value) => setTab(value as Tab)} unmountInactivePanels
        items={TABS.map(({ key, label }) => ({ id: key, label, content: {
          current: <CurrentStockView />, low: <LowStockView />, expiring: <ExpiringView />, valuation: <ValuationView />,
        }[key] }))} />

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
        <FeedbackState loading>Loading…</FeedbackState>
      ) : isEmpty ? (
        <FeedbackState>{emptyMessage}</FeedbackState>
      ) : (
        <TableRegion label="Stock report"><table className="table">
          <caption className="sr-only">Stock report</caption>
          {children}
        </table></TableRegion>
      )}
    </div>
  );
}

// 12-pharmacist.md §7.2 (D3, D6): batchNumber, unitCost, and
// deliveryNoteRef are all required — the minimum control on stock
// creation. Supplier is a managed list, with an "Other" branch that
// creates a new supplier row on submit (still not free-text-only — the
// row is persisted before the batch is written, so future restocks pick
// it from the dropdown).
const restockSchema = z.object({
  itemId: z.string().min(1, 'Select an item'),
  batchNumber: z.string().min(1, 'Enter the batch number'),
  expiryDate: z.string()
    .min(1, 'Enter the expiry date')
    .refine((v) => v >= todayInFacilityTz(), 'Expiry must be today or later — expired stock cannot be received'),
  quantity: z.coerce.number({ invalid_type_error: 'Enter a quantity' }).int().positive('Enter a quantity'),
  unitCostAmountMinor: z.coerce.number({ invalid_type_error: 'Enter the unit cost' }).min(0, 'Enter the unit cost'),
  supplierChoice: z.string().min(1, 'Select a supplier'),
  newSupplierName: z.string().optional(),
  deliveryNoteRef: z.string().min(1, 'Enter the delivery note reference'),
  notes: z.string().optional(),
}).refine(
  (v) => v.supplierChoice !== OTHER_SUPPLIER_VALUE || (v.newSupplierName?.trim().length ?? 0) > 0,
  { path: ['newSupplierName'], message: "Enter the new supplier's name" },
);
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
    watch,
    formState: { errors },
  } = useForm<RestockValues>({ resolver: zodResolver(restockSchema) });
  const supplierChoice = watch('supplierChoice');

  const submit = useMutation({
    mutationFn: async (values: RestockValues) => {
      // "Other" branch: create the supplier first, then restock against
      // the new id. Backend is idempotent on the trimmed name, so a
      // double-click doesn't double-create.
      const supplierId = values.supplierChoice === OTHER_SUPPLIER_VALUE
        ? (await createSupplier({ name: values.newSupplierName!.trim() }, accessToken as string)).id as string
        : values.supplierChoice;
      return restock(
        {
          itemId: values.itemId,
          storeId: store?.id as string,
          batchNumber: values.batchNumber,
          expiryDate: values.expiryDate,
          quantity: values.quantity,
          unitCostAmountMinor: values.unitCostAmountMinor,
          supplierId,
          deliveryNoteRef: values.deliveryNoteRef,
          notes: values.notes || undefined,
        },
        accessToken as string,
      );
    },
    onSuccess: () => {
      reset();
      setSelectedItem(null);
      queryClient.invalidateQueries({ queryKey: ['stock-reports'] });
      queryClient.invalidateQueries({ queryKey: ['suppliers'] });
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
              isOptionDisabled={(i) => i.selectable === false}
              onSelect={(i) => {
                setSelectedItem(i);
                setValue('itemId', i.id as string, { shouldValidate: true });
              }}
            />
            {selectedItem && (
              <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>
                Selected: <strong>{selectedItem.name}</strong>
                {selectedItem.strength && <> · {selectedItem.strength}</>}
                {selectedItem.form && <> · {selectedItem.form}</>}
              </div>
            )}
            {errors.itemId && <span className="field-error">{errors.itemId.message}</span>}
          </div>

          <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
            <Field label="Batch number" error={errors.batchNumber?.message}>
              <input className="clinical-control" {...register('batchNumber')} style={inputStyle} />
            </Field>
            <Field label="Expiry date" error={errors.expiryDate?.message}>
              <input className="clinical-control" type="date" min={todayInFacilityTz()} {...register('expiryDate')} style={inputStyle} />
            </Field>
            <Field label="Quantity" error={errors.quantity?.message}>
              <input className="clinical-control" type="text" inputMode="numeric" {...register('quantity')} style={inputStyle} />
            </Field>
            <Field label="Unit cost (TZS minor units)" error={errors.unitCostAmountMinor?.message}>
              <input className="clinical-control" type="text" inputMode="numeric" {...register('unitCostAmountMinor')} style={inputStyle} />
            </Field>
            <Field label="Supplier" error={errors.supplierChoice?.message}>
              <select className="clinical-control" {...register('supplierChoice')} style={inputStyle} defaultValue="">
                <option value="" disabled>Select a supplier</option>
                {(suppliers ?? []).map((s: SupplierResponse) => (
                  <option key={s.id} value={s.id as string}>
                    {s.name}
                  </option>
                ))}
                <option value={OTHER_SUPPLIER_VALUE}>Other — add a new supplier</option>
              </select>
            </Field>
            <Field label="Delivery note reference" error={errors.deliveryNoteRef?.message}>
              <input className="clinical-control" {...register('deliveryNoteRef')} style={inputStyle} />
            </Field>
            {supplierChoice === OTHER_SUPPLIER_VALUE && (
              <Field label="New supplier name" error={errors.newSupplierName?.message}>
                <input aria-label="e.g. Zenufa Laboratories" className="clinical-control"
                  {...register('newSupplierName')}
                  style={inputStyle}
                  placeholder="e.g. Zenufa Laboratories"
                  autoFocus
                />
              </Field>
            )}
          </div>

          <Field label="Notes (optional)">
            <textarea className="clinical-control" rows={2} {...register('notes')} style={inputStyle} />
          </Field>

          {submit.error && (
            <div className="form-error">
              {submit.error instanceof ApiError ? submit.error.message : 'Something went wrong. Try again.'}
            </div>
          )}
          {submit.isSuccess && <div style={{ color: 'var(--green-600)', fontSize: 'var(--text-sm)' }}>Batch received.</div>}

          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
            <button type="submit" className="btn btn-primary" disabled={submit.isPending || !store}>
              {submit.isPending ? 'Submitting…' : 'Receive stock'}
            </button>
            {!store && (
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
                Loading your pharmacy store…
              </span>
            )}
          </div>
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
