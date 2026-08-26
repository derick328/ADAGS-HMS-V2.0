import { authFetch } from './client';
import type { components } from './types';

export type ProposeSubstitutionRequest = components['schemas']['ProposeSubstitutionRequest'];
export type SubstitutionProposalResponse = components['schemas']['SubstitutionProposalResponse'];

/**
 * 12-pharmacist.md §6.1: pharmacist proposes an equivalent (generic or
 * therapeutic), the doctor approves or rejects. Only possible when the
 * prescriber's substitutionAllowed flag is set on the line.
 */
export function proposeSubstitution(
  prescriptionLineId: string,
  body: ProposeSubstitutionRequest,
  accessToken: string,
): Promise<SubstitutionProposalResponse> {
  return authFetch(`/api/prescription-lines/${prescriptionLineId}/substitution-proposals`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
