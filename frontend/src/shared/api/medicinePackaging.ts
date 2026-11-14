import { authFetch } from './client';

export interface MedicinePackaging {
  id: string | null;
  itemId: string;
  unit: string;
  factor: number;
  canSplit: boolean;
  version: number;
  status: string;
  blistersPerPack?: number | null;
  unitsPerBlister?: number | null;
}

export interface PendingMedicinePackaging {
  packaging: MedicinePackaging;
  itemName: string;
  baseUnit: string;
  reason: string;
  proposedBy: string;
  proposedForm?: string | null;
  proposedBaseUnit?: string | null;
}

export interface MedicinePackagingCatalogueItem {
  id: string;
  code: string;
  name: string;
  form: string;
  strength: string | null;
  baseUnit: string;
  active: boolean;
  packages: MedicinePackaging[];
}

export interface MedicinePackagingCataloguePage {
  items: MedicinePackagingCatalogueItem[];
  hasMore: boolean;
}

export type PackagingConfigurationFilter = 'CONFIGURED' | 'UNCONFIGURED' | 'ALL';

export function fetchMedicinePackagingCatalogue(query: string, configuration: PackagingConfigurationFilter,
  limit: number, offset: number,
  accessToken: string): Promise<MedicinePackagingCataloguePage> {
  const params = new URLSearchParams({ q: query, configuration, limit: String(limit), offset: String(offset) });
  return authFetch(`/api/medicine-packaging/catalogue?${params}`, accessToken);
}

export function fetchMedicinePackaging(itemId: string, accessToken: string): Promise<MedicinePackaging[]> {
  return authFetch(`/api/medicine-packaging/items/${itemId}`, accessToken);
}

export function proposeMedicinePackaging(itemId: string, body: {
  unit: string; quantityInBaseUnits: number; canSplit: boolean; reason: string;
  blistersPerPack?: number; unitsPerBlister?: number; replacesPackagingId?: string;
  proposedForm?: string; proposedBaseUnit?: string;
}, accessToken: string): Promise<MedicinePackaging> {
  return authFetch(`/api/medicine-packaging/items/${itemId}/proposals`, accessToken, {
    method: 'POST', body: JSON.stringify(body),
  });
}

export function fetchPendingMedicinePackaging(token: string): Promise<PendingMedicinePackaging[]> {
  return authFetch('/api/medicine-packaging/pending', token);
}

export function approveMedicinePackaging(id: string, token: string): Promise<MedicinePackaging> {
  return authFetch(`/api/medicine-packaging/${id}/approve`, token, { method: 'POST' });
}

export function rejectMedicinePackaging(id: string, reason: string, token: string): Promise<MedicinePackaging> {
  return authFetch(`/api/medicine-packaging/${id}/reject`, token,
    { method: 'POST', body: JSON.stringify({ reason }) });
}
