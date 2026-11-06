import { authFetch } from './client';

export interface PhysicalBatch {
  batchId: string; itemName: string; itemCode: string; batchNumber: string;
  baseUnit: string; status: string; expiryDate: string; quantity: number; storeId: string;
}
export interface StockAdjustment {
  id: string; batchId: string; itemId: string; storeId: string; type: string;
  quantityDelta: number; reason: string; status: string; requestedBy: string;
  requestedAt: string; reviewedBy: string | null; reviewedAt: string | null;
  reviewReason: string | null;
}

export function fetchPhysicalBatches(storeId: string, token: string): Promise<PhysicalBatch[]> {
  return authFetch(`/api/stock-adjustments/batches?storeId=${encodeURIComponent(storeId)}`, token);
}
export function fetchStockAdjustments(storeId: string, token: string): Promise<StockAdjustment[]> {
  return authFetch(`/api/stock-adjustments?storeId=${encodeURIComponent(storeId)}`, token);
}
export function requestStockAdjustment(body: { batchId: string; type: string; quantityDelta: number;
  reason: string; idempotencyKey: string }, token: string): Promise<StockAdjustment> {
  return authFetch('/api/stock-adjustments', token, { method: 'POST', body: JSON.stringify(body) });
}
export function reviewStockAdjustment(id: string, approve: boolean, reason: string,
  token: string): Promise<StockAdjustment> {
  return authFetch(`/api/stock-adjustments/${id}/review`, token, {
    method: 'POST', body: JSON.stringify({ approve, reason }),
  });
}
