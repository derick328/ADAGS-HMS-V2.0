import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Combobox } from '../../shared/components/Combobox';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { createLabStockRequest, fetchMyLabStockRequests } from '../../shared/api/labStockRequests';
import { fetchMyStore } from '../../shared/api/stores';
import { useAuth } from '../../shared/auth/AuthProvider';

const schema = z.object({
  itemId: z.string().min(1, 'Select an item'),
  itemLabel: z.string().optional(),
  requestedQuantity: z.coerce.number({ invalid_type_error: 'Enter a quantity' }).int().min(1, 'Enter a quantity of at least 1'),
  requestNotes: z.string().max(500, 'Keep notes under 500 characters').optional(),
});
type Values = z.infer<typeof schema>;

// 15-lab-technician.md §11: request reagents from pharmacy.
// requestingStoreId is resolved automatically (GET /api/stores/mine,
// lazily provisioning the lab's sub-store) — no store picker needed.
export function StockRequestsPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [selectedItem, setSelectedItem] = useState<ItemSearchResultResponse | null>(null);

  const { data: store } = useQuery({
    queryKey: ['stores', 'mine'],
    queryFn: () => fetchMyStore(accessToken as string),
    enabled: accessToken != null,
  });

  const { data: requests, isLoading } = useQuery({
    queryKey: ['lab-stock-requests', 'mine'],
    queryFn: () => fetchMyLabStockRequests(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 15_000,
  });

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const submit = useMutation({
    mutationFn: (values: Values) =>
      createLabStockRequest(
        { itemId: values.itemId, requestedQuantity: values.requestedQuantity, requestingStoreId: store?.id as string, requestNotes: values.requestNotes || undefined },
        accessToken as string,
      ),
    onSuccess: () => {
      reset();
      setSelectedItem(null);
      queryClient.invalidateQueries({ queryKey: ['lab-stock-requests', 'mine'] });
    },
  });

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Stock Requests</h1>
      </header>

      <form
        onSubmit={handleSubmit((v) => submit.mutate(v))}
        noValidate
        style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 480, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-6)' }}
      >
        <div>
          <Combobox<ItemSearchResultResponse>
            label="Item"
            placeholder="Search reagents and supplies"
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

        <div>
          <label htmlFor="quantity" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
            Quantity
          </label>
          <input id="quantity" type="text" inputMode="numeric" {...register('requestedQuantity')} style={inputStyle} />
          {errors.requestedQuantity && <span className="field-error">{errors.requestedQuantity.message}</span>}
        </div>

        <div>
          <label htmlFor="notes" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
            Notes (optional)
          </label>
          <textarea id="notes" rows={2} {...register('requestNotes')} style={inputStyle} />
          {errors.requestNotes && <span className="field-error">{errors.requestNotes.message}</span>}
        </div>

        {submit.error && (
          <div className="form-error" role="alert">
            Something went wrong. Try again.
          </div>
        )}

        <button type="submit" className="btn btn-primary" disabled={submit.isPending || !store} style={{ alignSelf: 'flex-start' }}>
          {submit.isPending ? 'Submitting…' : 'Request'}
        </button>
      </form>

      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {isLoading ? (
          <p style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>Loading…</p>
        ) : (requests ?? []).length === 0 ? (
          <p style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>No stock requests yet.</p>
        ) : (
          <table className="table">
            <caption className="sr-only">Stock requests</caption>
            <thead>
              <tr>
                <th>Requested</th>
                <th>Released</th>
                <th>Status</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {(requests ?? []).map((r) => (
                <tr key={r.id}>
                  <td className="tabular">{r.requestedQuantity}</td>
                  <td className="tabular">{r.releasedQuantity}</td>
                  <td>
                    <span className={`badge ${statusBadgeClass(r.status)}`}>{r.status}</span>
                    {(r.status === 'RELEASED' || r.status === 'PARTIALLY_RELEASED') && (
                      <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', marginTop: 2 }}>Received into lab store</div>
                    )}
                  </td>
                  <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{r.status === 'REJECTED' ? r.responseNotes : r.requestNotes}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function statusBadgeClass(status?: string): string {
  switch (status) {
    case 'RELEASED':
      return 'badge-success';
    case 'PARTIALLY_RELEASED':
      return 'badge-info';
    case 'REJECTED':
      return 'badge-critical';
    case 'CLOSED':
      return 'badge-neutral';
    default:
      return 'badge-warning';
  }
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
