import { authFetch } from './client';
import type { components } from './types';

export type CurrentStockResponse = components['schemas']['CurrentStockResponse'];

/** 12-pharmacist.md §6.1 / 08-doctor-navigation.md §4.1: per-batch balances — sum by itemId for a stock-status badge. */
export function fetchCurrentStock(accessToken: string): Promise<CurrentStockResponse[]> {
  return authFetch('/api/stock-reports/current-stock', accessToken);
}
