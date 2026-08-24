import { authFetch } from './client';
import type { components } from './types';

export type ItemSearchResultResponse = components['schemas']['ItemSearchResultResponse'];

/** 13-controlled-medicines-policy.md §4: every match returned, including CONTROLLED — visible and unselectable (selectable/unselectableReason), never silently absent. Backs the formulary typeahead — itemId here is what PlacePrescriptionRequest.itemId expects. */
export function searchItems(query: string, accessToken: string): Promise<ItemSearchResultResponse[]> {
  return authFetch(`/api/items/search?q=${encodeURIComponent(query)}`, accessToken);
}
