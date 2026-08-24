import { authFetch } from './client';
import type { components } from './types';

export type StoreResponse = components['schemas']['StoreResponse'];

/** Resolves (and lazily provisions) this technician's lab sub-store — the Stock Requests form's requestingStoreId, with no store picker needed. */
export function fetchMyStore(accessToken: string): Promise<StoreResponse> {
  return authFetch('/api/stores/mine', accessToken);
}
