import { authFetch } from './client';

export interface MedicinePackaging {
  id: string | null;
  itemId: string;
  unit: string;
  factor: number;
  canSplit: boolean;
  version: number;
  status: string;
}

export function fetchMedicinePackaging(itemId: string, accessToken: string): Promise<MedicinePackaging[]> {
  return authFetch(`/api/medicine-packaging/items/${itemId}`, accessToken);
}

export function proposeMedicinePackaging(itemId: string, body: {
  unit: string; quantityInBaseUnits: number; canSplit: boolean; reason: string;
}, accessToken: string): Promise<MedicinePackaging> {
  return authFetch(`/api/medicine-packaging/items/${itemId}/proposals`, accessToken, {
    method: 'POST', body: JSON.stringify(body),
  });
}

export function fetchPendingMedicinePackaging(token: string): Promise<MedicinePackaging[]> {
  return authFetch('/api/medicine-packaging/pending', token);
}

export function approveMedicinePackaging(id: string, token: string): Promise<MedicinePackaging> {
  return authFetch(`/api/medicine-packaging/${id}/approve`, token, { method: 'POST' });
}
