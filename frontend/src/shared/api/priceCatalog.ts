import { authFetch } from './client';
import type { components } from './types';

export type PriceCatalogItemResponse = components['schemas']['PriceCatalogItemResponse'];

/** 02-billing-architecture.md §4.3 ⚠12: payer-aware — pass encounterId so the price shown matches what will actually be charged. */
export function searchPriceCatalog(query: string, encounterId: string | undefined, accessToken: string): Promise<PriceCatalogItemResponse[]> {
  const params = new URLSearchParams({ q: query });
  if (encounterId) params.set('encounterId', encounterId);
  return authFetch(`/api/price-catalog/search?${params.toString()}`, accessToken);
}
