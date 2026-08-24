import { authFetch } from './client';
import type { components } from './types';

export type ItemSearchResultResponse = components['schemas']['ItemSearchResultResponse'];
export type ItemResponse = components['schemas']['ItemResponse'];

/** 13-controlled-medicines-policy.md §4: every match returned, including CONTROLLED — visible and unselectable (selectable/unselectableReason), never silently absent. Backs the formulary typeahead — itemId here is what PlacePrescriptionRequest.itemId expects. */
export function searchItems(query: string, accessToken: string): Promise<ItemSearchResultResponse[]> {
  return authFetch(`/api/items/search?q=${encodeURIComponent(query)}`, accessToken);
}

/** The catalogue item's own name/form/strength — the MAR's drug name comes from here via the prescription line's itemId. */
export function fetchItemById(id: string, accessToken: string): Promise<ItemResponse> {
  return authFetch(`/api/items/${id}`, accessToken);
}
