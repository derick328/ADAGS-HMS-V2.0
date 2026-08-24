import { authFetch } from './client';
import type { components } from './types';

export type VitalSignsResponse = components['schemas']['VitalSignsResponse'];

/** 07-doctor.md §5.1: the consultation left panel's triage-recorded vitals — newest first, every context. */
export function fetchVitalSignsByEncounter(encounterId: string, accessToken: string): Promise<VitalSignsResponse[]> {
  return authFetch(`/api/encounters/${encounterId}/vital-signs`, accessToken);
}
