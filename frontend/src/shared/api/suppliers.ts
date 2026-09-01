import { authFetch } from './client';
import type { components } from './types';

export type SupplierResponse = components['schemas']['SupplierResponse'];
export type CreateSupplierRequest = components['schemas']['CreateSupplierRequest'];

/** 12-pharmacist.md §7.2: "Supplier | Managed list, not free text" — the restock form's picker. */
export function fetchSuppliers(accessToken: string): Promise<SupplierResponse[]> {
  return authFetch('/api/suppliers', accessToken);
}

/** Ad-hoc create for the restock form's "Other" branch — idempotent on case-insensitive name. */
export function createSupplier(body: CreateSupplierRequest, accessToken: string): Promise<SupplierResponse> {
  return authFetch('/api/suppliers', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
