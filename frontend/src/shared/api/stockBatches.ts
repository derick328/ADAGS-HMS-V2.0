import { authFetch } from './client';
import type { components } from './types';

export type RestockRequest = components['schemas']['RestockRequest'];
export type StockBatchResponse = components['schemas']['StockBatchResponse'];

/**
 * The joined read model the admin's medication-and-pharmacy expense
 * page consumes. Typed inline here rather than pulled from generated
 * types because the endpoint (GET /api/stock-batches) was added
 * after the last openapi.yaml regeneration — CI regenerates on the
 * next backend build and the frontend build enforces it never lags,
 * so this typing is a short-lived bridge, not a permanent hand-write.
 */
export interface RestockLedgerEntry {
  batchId: string;
  itemId: string;
  itemName: string;
  batchNumber: string;
  receivedQuantity: number;
  unitCostAmountMinor: number;
  totalCostMinor: number;
  currencyCode: string;
  supplierId: string;
  supplierName: string;
  deliveryNoteRef: string;
  receivedAt: string;
  receivedById: string | null;
  receivedByName: string | null;
}

/**
 * 12-pharmacist.md §7.2 (D3, D6): batchNumber, unitCost, and
 * deliveryNoteRef are all required server-side — the minimum control on
 * stock creation.
 */
export function restock(body: RestockRequest, accessToken: string): Promise<StockBatchResponse> {
  return authFetch('/api/stock-batches', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/**
 * 18-admin.md §8 medication-and-pharmacy expense view: every
 * pharmacist restock in the window, joined server-side with the
 * RECEIPT movement so this row carries quantity and total spend, not
 * just per-unit cost. Gated on STOCK_READ.
 */
export function fetchRestocks(
  from: string,
  to: string,
  accessToken: string,
): Promise<RestockLedgerEntry[]> {
  return authFetch(`/api/stock-batches?from=${from}&to=${to}`, accessToken);
}
