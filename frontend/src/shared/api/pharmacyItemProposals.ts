import { authFetch } from './client';
import type { components } from './types';

export type ProposeNewItemRequest = components['schemas']['ProposeNewItemRequest'];
export type ProposePriceChangeRequest = components['schemas']['ProposePriceChangeRequest'];
export type PharmacyItemProposalResponse = components['schemas']['PharmacyItemProposalResponse'];

/** 12-pharmacist.md §9.2: a new catalogue item, price forced through admin approval — a pharmacist can never set a live price directly. */
export function proposeNewItem(body: ProposeNewItemRequest, accessToken: string): Promise<PharmacyItemProposalResponse> {
  return authFetch('/api/pharmacy-item-proposals/new-item', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** 12-pharmacist.md §9.2: a price change on an existing item, same approval queue as a new item. */
export function proposePriceChange(body: ProposePriceChangeRequest, accessToken: string): Promise<PharmacyItemProposalResponse> {
  return authFetch('/api/pharmacy-item-proposals/price-change', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

export function proposeFormChange(body: { itemId: string; proposedForm: string; proposedUnit?: string; justification: string },
  accessToken: string): Promise<PharmacyItemProposalResponse> {
  return authFetch('/api/pharmacy-item-proposals/form-change', accessToken,
    { method: 'POST', body: JSON.stringify(body) });
}
