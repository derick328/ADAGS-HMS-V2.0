import { authFetch } from './client';

export interface StockMovementRow {
  id: string;
  itemName: string;
  itemCode: string;
  batchNumber: string;
  quantityDelta: number;
  movementType: string;
  referenceType: string | null;
  occurredAt: string;
}

export function fetchStockMovements(storeId: string, token: string): Promise<StockMovementRow[]> {
  return authFetch(`/api/stock-movements?storeId=${encodeURIComponent(storeId)}`, token);
}
