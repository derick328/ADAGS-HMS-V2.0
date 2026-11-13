import { authFetch } from './client';

export interface MedicinePurchaseCost {
  unit: string;
  amountMinor: number;
  updatedBy: string;
  updatedAt: string;
}

export function fetchMedicinePurchaseCost(itemId: string, token: string): Promise<MedicinePurchaseCost | null> {
  return authFetch(`/api/medicines/${itemId}/default-purchase-cost`, token);
}

export function fetchMedicinePurchaseCostHistory(itemId: string, token: string): Promise<MedicinePurchaseCost[]> {
  return authFetch(`/api/medicines/${itemId}/default-purchase-cost/history`, token);
}

export function updateMedicinePurchaseCost(itemId: string, unit: string, amountMinor: number, token: string): Promise<MedicinePurchaseCost> {
  return authFetch(`/api/medicines/${itemId}/default-purchase-cost`, token, {
    method: 'PUT', body: JSON.stringify({ unit, amountMinor }),
  });
}
