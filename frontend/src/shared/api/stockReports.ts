import { authFetch } from './client';
import type { components } from './types';

export type CurrentStockResponse = components['schemas']['CurrentStockResponse'];
export type LowStockResponse = components['schemas']['LowStockResponse'];
export type ExpiringBatchResponse = components['schemas']['ExpiringBatchResponse'];
export type StockValuationResponse = components['schemas']['StockValuationResponse'];

/** 12-pharmacist.md §6.1 / 08-doctor-navigation.md §4.1: per-batch balances — sum by itemId for a stock-status badge. */
export function fetchCurrentStock(accessToken: string): Promise<CurrentStockResponse[]> {
  return authFetch('/api/stock-reports/current-stock', accessToken);
}

/** 12-pharmacist.md §7.1: items below their reorder_level. */
export function fetchLowStock(accessToken: string): Promise<LowStockResponse[]> {
  return authFetch('/api/stock-reports/low-stock', accessToken);
}

/** 12-pharmacist.md §7.1/§7.3: batches expiring within 90 days — the expiry watchlist. */
export function fetchExpiringBatches(accessToken: string): Promise<ExpiringBatchResponse[]> {
  return authFetch('/api/stock-reports/expiring-batches', accessToken);
}

/** 12-pharmacist.md §7.1: SUM(quantity × unit_cost) by store — needed by the accountant, otherwise uncomputable. */
export function fetchStockValuation(accessToken: string): Promise<StockValuationResponse[]> {
  return authFetch('/api/stock-reports/valuation', accessToken);
}
