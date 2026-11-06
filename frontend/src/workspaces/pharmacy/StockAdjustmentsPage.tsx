import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { fetchPhysicalBatches, fetchStockAdjustments, requestStockAdjustment,
  reviewStockAdjustment } from '../../shared/api/stockAdjustments';
import { fetchAuthorizedStockStores } from '../../shared/api/stores';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';

export function StockAdjustmentsPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canRequest = usePermission('STOCK_ADJUST_REQUEST');
  const canApprove = usePermission('STOCK_WRITE_OFF_APPROVE');
  const [batchId, setBatchId] = useState('');
  const [type, setType] = useState('DAMAGED');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [key, setKey] = useState(crypto.randomUUID());
  const [selectedStoreId, setSelectedStoreId] = useState('');
  const { data: stores } = useQuery({ queryKey: ['stores', 'authorized-stock'],
    queryFn: () => fetchAuthorizedStockStores(accessToken as string), enabled: accessToken != null });
  const storeId = selectedStoreId || stores?.[0]?.id || '';
  const { data: batches } = useQuery({ queryKey: ['stock-adjustments', 'batches', storeId],
    queryFn: () => fetchPhysicalBatches(storeId, accessToken as string),
    enabled: accessToken != null && !!storeId });
  const { data: adjustments, isLoading } = useQuery({ queryKey: ['stock-adjustments', storeId],
    queryFn: () => fetchStockAdjustments(storeId, accessToken as string),
    enabled: accessToken != null && !!storeId });
  const refresh = () => { queryClient.invalidateQueries({ queryKey: ['stock-adjustments'] });
    queryClient.invalidateQueries({ queryKey: ['stock-reports'] }); };
  const request = useMutation({ mutationFn: () => {
    const parsed = Number(quantity);
    const delta = type === 'QUARANTINE' ? 0 : type === 'PHYSICAL_COUNT' || type === 'RECEIPT_CORRECTION' ? parsed : -parsed;
    if (!batchId || !reason.trim() || !Number.isSafeInteger(delta) ||
        type !== 'QUARANTINE' && delta === 0 || type !== 'QUARANTINE' && !quantity.trim()) {
      setError('Select a batch, enter a whole quantity change, and explain the correction.');
      throw new Error('Invalid adjustment');
    }
    setError('');
    return requestStockAdjustment({ batchId, type, quantityDelta: delta, reason: reason.trim(),
      idempotencyKey: key }, accessToken as string);
  }, onSuccess: () => { setBatchId(''); setQuantity(''); setReason(''); setKey(crypto.randomUUID()); refresh(); } });
  const review = useMutation({ mutationFn: ({ id, approve }: { id: string; approve: boolean }) =>
    reviewStockAdjustment(id, approve, approve ? '' : 'Rejected after review', accessToken as string),
    onSuccess: refresh });

  return <div style={{ display: 'grid', gap: 'var(--space-4)' }}>
    <label>Store <select className="clinical-control" value={storeId} onChange={(e) => { setSelectedStoreId(e.target.value); setBatchId(''); }}>
      {(stores ?? []).map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}
    </select></label>
    {canRequest && <section className="stock-in-card">
      <h3>Request correction</h3>
      <div className="stock-in-grid">
        <label>Batch <select className="clinical-control" value={batchId} onChange={(e) => { setBatchId(e.target.value); setKey(crypto.randomUUID()); }}>
          <option value="">Select physical stock</option>
          {(batches ?? []).map((batch) => <option key={batch.batchId} value={batch.batchId}>
            {batch.itemName} · {batch.batchNumber} · {batch.quantity} {batch.baseUnit} · {batch.status}
          </option>)}
        </select></label>
        <label>Reason type <select className="clinical-control" value={type} onChange={(e) => { setType(e.target.value); setKey(crypto.randomUUID()); }}>
          <option value="DAMAGED">Damaged stock removed</option><option value="EXPIRED">Expired stock removed</option>
          <option value="LOST">Lost stock</option><option value="PHYSICAL_COUNT">Physical count correction</option>
          <option value="RECEIPT_CORRECTION">Receiving quantity correction</option>
          <option value="QUARANTINE">Quarantine entire batch</option>
        </select></label>
        {type !== 'QUARANTINE' && <label>{type === 'PHYSICAL_COUNT' || type === 'RECEIPT_CORRECTION' ? 'Signed base-unit change' : 'Base units removed'}
          <input className="clinical-control" type="number" step="1" value={quantity} onChange={(e) => { setQuantity(e.target.value); setKey(crypto.randomUUID()); }} /></label>}
        <label>Reason <textarea className="clinical-control" value={reason} onChange={(e) => { setReason(e.target.value); setKey(crypto.randomUUID()); }} /></label>
      </div>
      {error && <p className="field-error" role="alert">{error}</p>}
      {request.error && !error && <p className="field-error" role="alert">{request.error instanceof ApiError ? request.error.message : 'Could not request adjustment.'}</p>}
      <button type="button" className="btn btn-primary" disabled={request.isPending} onClick={() => request.mutate()}>
        {request.isPending ? 'Submitting…' : 'Request adjustment'}</button>
    </section>}
    <section className="stock-in-card"><h3>Adjustment history</h3>
      {isLoading ? <FeedbackState loading>Loading…</FeedbackState> : !adjustments?.length ?
        <FeedbackState>No adjustments requested.</FeedbackState> :
        <TableRegion label="Stock adjustments"><table className="table"><thead><tr><th>Type</th><th>Batch</th><th>Base-unit change</th><th>Reason</th><th>Status</th><th>Review</th></tr></thead><tbody>
          {adjustments.map((adjustment) => <tr key={adjustment.id}>
            <td>{adjustment.type}</td><td>{batches?.find((batch) => batch.batchId === adjustment.batchId)?.batchNumber ?? adjustment.batchId}</td>
            <td className="tabular">{adjustment.quantityDelta}</td><td>{adjustment.reason}</td><td>{adjustment.status}</td>
            <td>{canApprove && adjustment.status === 'PENDING' ? <span style={{ display: 'flex', gap: 'var(--space-1)' }}>
              <button type="button" className="btn btn-primary" disabled={review.isPending} onClick={() => review.mutate({ id: adjustment.id, approve: true })}>Approve</button>
              <button type="button" className="btn btn-secondary" disabled={review.isPending} onClick={() => review.mutate({ id: adjustment.id, approve: false })}>Reject</button>
            </span> : adjustment.reviewReason ?? '—'}</td>
          </tr>)}</tbody></table></TableRegion>}
    </section>
  </div>;
}
