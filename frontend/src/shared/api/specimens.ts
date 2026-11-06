import { authFetch } from './client';
import type { components } from './types';

export type SpecimenResponse = components['schemas']['SpecimenResponse'];
export type CollectSpecimenRequest = components['schemas']['CollectSpecimenRequest'];
export type SpecimenQuality = NonNullable<CollectSpecimenRequest['quality']>;

/**
 * 15-lab-technician.md §4: accept sample — accession + quality assessment
 * as one action. The billing gate runs server-side, live, at this call; a
 * 402 (ApiError.status === 402) carries outstandingAmountMinor/serviceName
 * for the "send to cashier" action, never cached from order time.
 */
export function collectSpecimen(body: CollectSpecimenRequest & { sampleType?: string; sampleTypeOverrideReason?: string }, accessToken: string): Promise<SpecimenResponse> {
  return authFetch('/api/specimens', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
