import { authFetch } from './client';
import type { components } from './types';

export type LabItemProposalResponse = components['schemas']['LabItemProposalResponse'];
export type ProposeLabItemRequest = components['schemas']['ProposeLabItemRequest'];
export type ProposedParameterRequest = components['schemas']['ProposedParameterRequest'];

// 15-lab-technician.md §10: proposals only — submit-only screen, no review
// queue here (mirrors the pharmacist's proposal screen, which has no "my
// proposals" list either). Admin approval (LAB_ITEM_APPROVE) is a later,
// separate admin-workspace build against this same table.
export function proposeLabItem(body: ProposeLabItemRequest, accessToken: string): Promise<LabItemProposalResponse> {
  return authFetch('/api/lab-item-proposals', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
