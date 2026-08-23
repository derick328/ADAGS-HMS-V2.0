import { authFetch } from './client';
import type { components } from './types';

export type PharmacyItemProposalResponse = components['schemas']['PharmacyItemProposalResponse'];
export type ReviewPharmacyItemProposalRequest = components['schemas']['ReviewPharmacyItemProposalRequest'];

export function fetchPendingPharmacyItemProposals(accessToken: string): Promise<PharmacyItemProposalResponse[]> {
  return authFetch('/api/pharmacy-item-proposals/pending', accessToken);
}

export function reviewPharmacyItemProposal(
  id: string,
  body: ReviewPharmacyItemProposalRequest,
  accessToken: string,
): Promise<PharmacyItemProposalResponse> {
  return authFetch(`/api/pharmacy-item-proposals/${id}/review`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
