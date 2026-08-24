import { authFetch } from './client';
import type { components } from './types';

export type RestockRequest = components['schemas']['RestockRequest'];
export type StockBatchResponse = components['schemas']['StockBatchResponse'];

/**
 * 12-pharmacist.md §7.2 (D3, D6): batchNumber, unitCost, and
 * deliveryNoteRef are all required server-side — the minimum control on
 * stock creation.
 */
export function restock(body: RestockRequest, accessToken: string): Promise<StockBatchResponse> {
  return authFetch('/api/stock-batches', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
