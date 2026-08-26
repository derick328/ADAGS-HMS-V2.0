import { authFetch } from './client';
import type { components } from './types';

export type SupplierResponse = components['schemas']['SupplierResponse'];

/** 12-pharmacist.md §7.2: "Supplier | Managed list, not free text" — the restock form's picker. */
export function fetchSuppliers(accessToken: string): Promise<SupplierResponse[]> {
  return authFetch('/api/suppliers', accessToken);
}
