import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  fetchPendingLabStockRequests,
  rejectLabStockRequest,
  releaseLabStockRequest,
  type LabStockRequestQueueRowResponse,
} from '../../shared/api/labStockRequests';
import { fetchCurrentStock } from '../../shared/api/stockReports';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';

// 12-pharmacist.md §8: request-and-release. A release is a transfer, not
// a dispense — two movement rows, no billing gate. Partial release is
// permitted; the remainder stays open or closes with a note.
export function LabStockRequestsPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['lab-stock-requests', 'pending'],
    queryFn: () => fetchPendingLabStockRequests(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 15_000,
  });

  const rows = data ?? [];

  function onDone() {
    setExpandedId(null);
    queryClient.invalidateQueries({ queryKey: ['lab-stock-requests', 'pending'] });
    queryClient.invalidateQueries({ queryKey: ['stock-reports'] });
  }

  return (
    <div>
      <PageHeader title={<>Lab stock requests</>} description={<>Reagent requests from lab technicians, oldest first.</>} />

      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {isLoading ? (
          <FeedbackState loading>Loading…</FeedbackState>
        ) : rows.length === 0 ? (
          <FeedbackState>No open requests.</FeedbackState>
        ) : (
          <TableRegion label="Pending lab stock requests"><table className="table">
            <caption className="sr-only">Pending lab stock requests</caption>
            <thead>
              <tr>
                <th>Item</th>
                <th>Requested</th>
                <th>Released</th>
                <th>Requested by</th>
                <th>When</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <Fragment key={row.id}>
                  <tr>
                    <td>
                      {row.itemName} <span style={{ color: 'var(--text-muted)' }}>· {row.itemCode}</span>
                      {row.requestNotes && (
                        <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-xs)', marginTop: 2 }}>{row.requestNotes}</div>
                      )}
                    </td>
                    <td className="tabular">{row.remaining} of {row.requestedQuantity}</td>
                    <td className="tabular">{row.releasedQuantity}</td>
                    <td>{row.requestedByUsername}</td>
                    <td className="tabular">{formatDateTime(row.requestedAt)}</td>
                    <td>
                      <button type="button" className="btn btn-primary" onClick={() => setExpandedId(expandedId === row.id ? null : (row.id as string))}>
                        {expandedId === row.id ? 'Close' : 'Respond'}
                      </button>
                    </td>
                  </tr>
                  {expandedId === row.id && (
                    <tr>
                      <td colSpan={6} style={{ background: 'var(--bg-page)' }}>
                        <RespondPanel row={row} onDone={onDone} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>
    </div>
  );
}

const releaseSchema = z.object({
  batchId: z.string().min(1, 'Select a batch'),
  quantity: z.coerce.number({ invalid_type_error: 'Enter a quantity' }).int().positive('Enter a quantity'),
});
type ReleaseValues = z.infer<typeof releaseSchema>;

const rejectSchema = z.object({ responseNotes: z.string().min(1, 'Enter a reason — required on rejection') });
type RejectValues = z.infer<typeof rejectSchema>;

function RespondPanel({ row, onDone }: { row: LabStockRequestQueueRowResponse; onDone: () => void }) {
  const { accessToken } = useAuth();
  const [mode, setMode] = useState<'release' | 'reject'>('release');

  // Batches available for this item, so the pharmacist can pick which
  // batch the transfer draws from — same current-stock report the Stock
  // screen shows, filtered to this item.
  const { data: currentStock } = useQuery({
    queryKey: ['stock-reports', 'current-stock'],
    queryFn: () => fetchCurrentStock(accessToken as string),
    enabled: accessToken != null,
  });
  const batchesForItem = (currentStock ?? []).filter((s) => s.itemId === row.itemId);

  const releaseForm = useForm<ReleaseValues>({ resolver: zodResolver(releaseSchema) });
  const releaseSubmit = useMutation({
    mutationFn: (values: ReleaseValues) => releaseLabStockRequest(row.id as string, values, accessToken as string),
    onSuccess: onDone,
  });

  const rejectForm = useForm<RejectValues>({ resolver: zodResolver(rejectSchema) });
  const rejectSubmit = useMutation({
    mutationFn: (values: RejectValues) => rejectLabStockRequest(row.id as string, values, accessToken as string),
    onSuccess: onDone,
  });

  return (
    <div style={{ padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 480 }}>
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        <button type="button" className={mode === 'release' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setMode('release')}>
          Release
        </button>
        <button type="button" className={mode === 'reject' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setMode('reject')}>
          Reject
        </button>
      </div>

      {mode === 'release' && (
        <form onSubmit={releaseForm.handleSubmit((v) => releaseSubmit.mutate(v))} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <div>
            <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>Batch</label>
            <select className="clinical-control" {...releaseForm.register('batchId')} style={inputStyle}>
              <option value="">Select a batch</option>
              {batchesForItem.map((b) => (
                <option key={b.batchId} value={b.batchId}>
                  {b.batchNumber} · {b.quantity} available
                </option>
              ))}
            </select>
            {releaseForm.formState.errors.batchId && <span className="field-error">{releaseForm.formState.errors.batchId.message}</span>}
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>Quantity (≤ {row.remaining} remaining)</label>
            <input className="clinical-control" type="text" inputMode="numeric" {...releaseForm.register('quantity')} style={inputStyle} />
            {releaseForm.formState.errors.quantity && <span className="field-error">{releaseForm.formState.errors.quantity.message}</span>}
          </div>
          {releaseSubmit.error && <div className="form-error">Something went wrong. Try again.</div>}
          <button type="submit" className="btn btn-primary" disabled={releaseSubmit.isPending} style={{ alignSelf: 'flex-start' }}>
            {releaseSubmit.isPending ? 'Releasing…' : 'Release'}
          </button>
        </form>
      )}

      {mode === 'reject' && (
        <form onSubmit={rejectForm.handleSubmit((v) => rejectSubmit.mutate(v))} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <div>
            <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>Reason</label>
            <textarea className="clinical-control" rows={2} {...rejectForm.register('responseNotes')} style={inputStyle} />
            {rejectForm.formState.errors.responseNotes && <span className="field-error">{rejectForm.formState.errors.responseNotes.message}</span>}
          </div>
          {rejectSubmit.error && <div className="form-error">Something went wrong. Try again.</div>}
          <button type="submit" className="btn btn-primary" disabled={rejectSubmit.isPending} style={{ alignSelf: 'flex-start' }}>
            {rejectSubmit.isPending ? 'Rejecting…' : 'Reject'}
          </button>
        </form>
      )}
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
