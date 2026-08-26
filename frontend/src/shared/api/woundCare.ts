import { authFetch } from './client';
import type { components } from './types';

export type WoundCareResponse = components['schemas']['WoundCareResponse'];
export type RecordWoundCareRequest = components['schemas']['RecordWoundCareRequest'];

export function fetchWoundCare(admissionId: string, accessToken: string): Promise<WoundCareResponse[]> {
  return authFetch(`/api/admissions/${admissionId}/wound-care`, accessToken);
}

/** 17-ward-nurse.md §6: signsOfInfection=true notifies the covering doctor immediately (server-side, not a client concern). */
export function recordWoundCare(
  admissionId: string,
  body: RecordWoundCareRequest,
  accessToken: string,
): Promise<WoundCareResponse> {
  return authFetch(`/api/admissions/${admissionId}/wound-care`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
